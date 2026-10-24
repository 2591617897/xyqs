package com.campus.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.api.R;
import com.campus.common.client.UserClient;
import com.campus.common.client.dto.UserBrief;
import com.campus.message.dto.NotifyReq;
import com.campus.message.entity.Notification;
import com.campus.message.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 通知中心：
 * 1. 内部 Feign 入口（/internal/notifications）用于业务服务（post / wall / goods / user）异步发通知；
 * 2. 公开列表接口（/api/messages/notifications）给前端拉取分 tab 通知；
 * 3. 所有写入走 try-catch，通知失败不阻塞主业务流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserClient userClient;

    private static final int SNAPSHOT_LEN = 300;

    /**
     * 发送通知（Feign 入口调用）。异步执行，任何异常只记日志不向上抛。
     * 自通知过滤（点赞自己的帖子等）：receiverId == actorId 时跳过。
     */
    public void send(NotifyReq req) {
        if (req == null || req.getReceiverId() == null || req.getType() == null) {
            return;
        }
        if (req.getActorId() != null && req.getActorId().equals(req.getReceiverId())) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setType(req.getType());
            n.setReceiverId(req.getReceiverId());
            n.setActorId(req.getActorId());
            n.setTargetType(req.getTargetType());
            n.setTargetId(req.getTargetId());
            n.setContentSnapshot(truncate(req.getContentSnapshot()));
            n.setIsRead(0);
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("[notify] send failed type={} receiverId={} targetId={}: {}",
                    req.getType(), req.getReceiverId(), req.getTargetId(), e.getMessage());
        }
    }

    /** 批量发送（同类型多条时用，比如一条评论 @ 了 5 个人） */
    public void sendBatch(List<NotifyReq> reqs) {
        if (reqs == null || reqs.isEmpty()) return;
        for (NotifyReq r : reqs) send(r);
    }

    /** 按接收人 + 类型分页，列表触发者 enrich 昵称头像（1 次 Feign 批量） */
    public R.Page<Notification> list(Long userId, String type, long page, long size) {
        LambdaQueryWrapper<Notification> q = Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getReceiverId, userId)
                .eq(type != null && !type.isBlank(), Notification::getType, type)
                .orderByDesc(Notification::getCreatedAt);
        Page<Notification> p = notificationMapper.selectPage(new Page<>(page, size), q);
        enrichActors(p.getRecords());
        return R.Page.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 批量 enrich 触发者昵称头像（1 次 Feign 避免 N+1） */
    private void enrichActors(List<Notification> records) {
        if (records == null || records.isEmpty()) return;
        List<Long> ids = records.stream()
                .map(Notification::getActorId)
                .filter(Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) return;
        Map<Long, UserBrief> briefMap = Map.of();
        try {
            R<List<UserBrief>> r = userClient.getBriefs(ids);
            if (r != null && r.getData() != null) {
                briefMap = r.getData().stream().collect(Collectors.toMap(UserBrief::getId, b -> b));
            }
        } catch (Exception e) {
            log.warn("[notify] enrich actors failed: {}", e.getMessage());
        }
        final Map<Long, UserBrief> m = briefMap;
        records.forEach(n -> {
            UserBrief b = m.get(n.getActorId());
            if (b != null) {
                n.setActorName(b.getNickname());
                n.setActorAvatar(b.getAvatar());
            }
        });
    }

    /** 各 tab 未读数 */
    public UnreadStats unreadStats(Long userId) {
        UnreadStats s = new UnreadStats();
        s.all = notificationMapper.countAllUnread(userId);
        s.like = notificationMapper.countUnreadByType(userId, "LIKE_GOODS");
        // LIKE_POST / LIKE_WALL 也算赞类
        s.like += notificationMapper.countUnreadByType(userId, "LIKE_POST");
        s.like += notificationMapper.countUnreadByType(userId, "LIKE_WALL");
        s.follow = notificationMapper.countUnreadByType(userId, "NEW_FOLLOWER");
        s.mention = notificationMapper.countUnreadByType(userId, "MENTION");
        s.commentPost = notificationMapper.countUnreadByType(userId, "COMMENT_POST");
        s.commentWall = notificationMapper.countUnreadByType(userId, "COMMENT_WALL");
        s.system = notificationMapper.countUnreadByType(userId, "SYSTEM");
        return s;
    }

    public int markReadByType(Long userId, String type) {
        return notificationMapper.markReadByType(userId, type);
    }

    public int markAllRead(Long userId) {
        return notificationMapper.markAllRead(userId);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > SNAPSHOT_LEN ? s.substring(0, SNAPSHOT_LEN) : s;
    }

    /** 未读统计汇总（给前端 tab 顶部 badge 用） */
    public static class UnreadStats {
        public int all;
        public int like;       // 赞 + 收藏
        public int follow;     // 新增粉丝
        public int mention;    // 评论与@
        public int commentPost;
        public int commentWall;
        public int system;     // 官方
    }
}
