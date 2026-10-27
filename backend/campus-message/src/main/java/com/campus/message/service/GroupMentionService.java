package com.campus.message.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.campus.message.entity.GroupMention;
import com.campus.message.entity.GroupMember;
import com.campus.message.mapper.GroupMapper;
import com.campus.message.mapper.GroupMemberMapper;
import com.campus.message.mapper.GroupMentionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @提醒的唯一入口。两条完全不同的路径，混在一起就会写放大：
 *
 * <table border="1">
 *   <tr><th>类型</th><th>存储</th><th>写行数</th></tr>
 *   <tr><td>@我（指定）</td><td>t_group_mention + mention_unread+1</td><td>最多 10 行</td></tr>
 *   <tr><td>@所有人</td><td>t_group.last_at_all_msg_id</td><td><b>0 行</b></td></tr>
 * </table>
 *
 * <p>如果 @所有人 也按「给每个成员 +1」实现，1 万人的群一次 @全体就是 1 万行 UPDATE：
 * 锁表、主从延迟、undo log 膨胀，而且每次 @全体 都会这样。这是本模块最不能犯的错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMentionService {

    /** 单条消息最多 @ 10 人，超过直接截断（不是报错，用户感知不到） */
    public static final int MAX_AT_PER_MESSAGE = 10;

    private final GroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final GroupMentionMapper mentionMapper;

    /**
     * 落地一条消息的 @ 效果。
     *
     * @param groupId   群
     * @param messageId 刚插入的消息 id
     * @param senderId  发送者（@ 到自己不提醒）
     * @param atUserIds 被 @ 的成员 id（可为空）
     * @param atAll     是否 @所有人
     */
    public void apply(Long groupId, Long messageId, Long senderId,
                      List<Long> atUserIds, boolean atAll) {
        if (atAll) {
            // 零写放大：只更新群的一行
            groupMapper.updateLastAtAll(groupId, messageId);
            return;
        }
        if (atUserIds == null || atUserIds.isEmpty()) {
            return;
        }

        List<GroupMention> rows = new ArrayList<>();
        for (Long uid : atUserIds) {
            if (uid == null || uid.equals(senderId)) {
                continue;   // 自己 @ 自己不提醒
            }
            // 只提醒「在群」的成员：@ 一个已退群的人只会产生一条永远点不开的红点
            GroupMember target = memberMapper.selectActive(groupId, uid);
            if (target == null) {
                continue;
            }
            GroupMention m = new GroupMention();
            m.setId(IdWorker.getId());
            m.setGroupId(groupId);
            m.setMessageId(messageId);
            m.setUserId(uid);
            m.setReadFlag(0);
            rows.add(m);
        }
        if (rows.isEmpty()) {
            return;
        }
        mentionMapper.insertBatch(rows);
        // 未读数用「读时算」还是「写时 +1」？这里选写时 +1：
        // @ 是低频事件（单条最多 10 人），+1 的成本可以忽略，换来列表页零查询。
        for (GroupMention m : rows) {
            memberMapper.incrMention(groupId, m.getUserId());
        }
    }

    /** 上报已读时清掉本群所有 @我 记录（游标已经越过它们了） */
    public void readAll(Long groupId, Long userId) {
        mentionMapper.markReadByGroup(groupId, userId);
    }

    public int unreadCount(Long groupId, Long userId) {
        return mentionMapper.countUnread(groupId, userId);
    }

    public List<Long> unreadMessageIds(Long groupId, Long userId, int limit) {
        return mentionMapper.selectUnreadMessageIds(groupId, userId, limit);
    }
}
