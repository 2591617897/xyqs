package com.campus.message.service;

import com.campus.message.entity.GroupMute;
import com.campus.message.mapper.GroupMuteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群禁言：事实源在 {@code t_group_mute}，进程内加一层短缓存。
 *
 * <p>为什么需要缓存：万人群每次发言都查一次禁言表，等于每条消息多一次 DB 往返；
 * 而禁言是低频操作，缓存 60 秒完全够用。缓存里同时记「没有禁言」这个结论
 * （用 {@link Cached#mute} 为 null 表示），否则每个正常用户每次发言都穿透查库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMuteService {

    private static final long CACHE_MS = 60_000L;

    private final GroupMuteMapper muteMapper;

    private record Cached(GroupMute mute, long expireAt) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /** 当前生效的禁言，没有则返回 null */
    public GroupMute active(Long groupId, Long userId) {
        String key = groupId + ":" + userId;
        Cached c = cache.get(key);
        long now = System.currentTimeMillis();
        if (c != null && c.expireAt() > now) {
            return c.mute();
        }
        GroupMute mute = muteMapper.selectActive(groupId, userId, LocalDateTime.now());
        cache.put(key, new Cached(mute, now + CACHE_MS));
        return mute;
    }

    /**
     * 禁言。先把旧的生效记录收掉再插新的 —— 否则同一人会有多条 status=1 的记录，
     * 后台「解除禁言」就会只解掉其中一条，人还是发不出言。
     */
    public void mute(Long groupId, Long userId, Integer hours, String reason, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endAt = (hours == null || hours <= 0) ? null : now.plusHours(hours);

        muteMapper.closeActive(groupId, userId);

        GroupMute mute = new GroupMute();
        mute.setGroupId(groupId);
        mute.setUserId(userId);
        mute.setStartAt(now);
        mute.setEndAt(endAt);
        mute.setReason(reason == null ? "" : reason.trim());
        mute.setOperatorId(operatorId == null ? 0L : operatorId);
        mute.setStatus(1);
        mute.setCreatedAt(now);
        muteMapper.insert(mute);

        cache.remove(groupId + ":" + userId);
        log.info("[group-mute] 禁言 group={} user={} 到 {} 原因={}",
                groupId, userId, endAt == null ? "永久" : endAt, reason);
    }

    /** 解除禁言：把所有生效记录一并收掉（历史上可能已有多条） */
    public int unmute(Long groupId, Long userId) {
        int n = muteMapper.closeActive(groupId, userId);
        cache.remove(groupId + ":" + userId);
        return n;
    }

    /** 禁言到期时间的人话文案 */
    public static String describe(GroupMute mute) {
        if (mute == null) {
            return "";
        }
        if (mute.getEndAt() == null) {
            return "永久";
        }
        return mute.getEndAt().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
}
