package com.campus.message.service;

import com.campus.message.entity.Group;
import com.campus.message.entity.GroupMember;
import com.campus.message.entity.GroupMessage;
import com.campus.message.entity.GroupMessageArchive;
import com.campus.message.mapper.GroupMessageArchiveMapper;
import com.campus.message.mapper.GroupMessageMapper;
import com.campus.message.mapper.GroupMentionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 群消息自动归档：热表只保留 12 个月。
 *
 * <p>为什么必须归档：单校 1 万人、日发 2400 条，三年累计约 260 万行/校。
 * 带 {@code (group_id,id)} 索引时 B+ 树只有 3 层、单查 3~4 次 IO，MySQL 完全够用 ——
 * 所以<b>不做分表</b>，只把冷数据挪走，让热表常驻千万级以内。
 *
 * <p>三条硬约束：
 * <ol>
 *   <li>分批 500 + 每批一个独立事务：一次性迁百万行会锁表、撑爆 undo log；</li>
 *   <li>先 {@code INSERT IGNORE} 进归档表，再删热表：中途崩了重跑不丢数据（可重试）；</li>
 *   <li>同步清理对应的 {@code t_group_mention}：否则 @ 提醒会指向已被删除的消息。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupArchiveService {

    /** 热表保留窗口：12 个月 */
    private static final int KEEP_MONTHS = 12;

    private final GroupMessageMapper messageMapper;
    private final GroupMessageArchiveMapper archiveMapper;
    private final GroupMentionMapper mentionMapper;

    /**
     * 迁移一批。
     *
     * @return 实际迁移条数，0 表示没有更早的数据了
     */
    @Transactional(rollbackFor = Exception.class)
    public int moveBatch(int limit) {
        LocalDateTime before = LocalDateTime.now().minusMonths(KEEP_MONTHS);
        List<GroupMessage> batch = messageMapper.selectOlderThan(before, limit);
        if (batch.isEmpty()) {
            return 0;
        }

        List<GroupMessageArchive> rows = new ArrayList<>(batch.size());
        List<Long> ids = new ArrayList<>(batch.size());
        for (GroupMessage m : batch) {
            GroupMessageArchive a = new GroupMessageArchive();
            a.setId(m.getId());
            a.setGroupId(m.getGroupId());
            a.setSenderId(m.getSenderId());
            a.setSenderName(m.getSenderName());
            a.setSenderAvatar(m.getSenderAvatar());
            a.setType(m.getType());
            a.setContent(m.getContent());
            a.setAtType(m.getAtType());
            a.setAtUserIds(m.getAtUserIds());
            a.setReplyToId(m.getReplyToId());
            a.setReplyToName(m.getReplyToName());
            a.setReplyToText(m.getReplyToText());
            a.setDeleted(m.getDeleted());
            a.setDeletedBy(m.getDeletedBy());
            a.setCreatedAt(m.getCreatedAt());
            rows.add(a);
            ids.add(m.getId());
        }

        archiveMapper.insertBatch(rows);
        // 12 个月前的 @ 提醒用户早已读过、也没有入口能翻到，直接清理（详见 Mapper 注释）
        mentionMapper.deleteByMessageIds(ids);
        messageMapper.deleteByIds(ids);
        return batch.size();
    }

    /** 单次归档任务可迁移的上限，防止极端情况下跑太久 */
    public static int maxBatchesPerRun() {
        return 200;
    }
}
