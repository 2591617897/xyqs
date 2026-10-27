package com.campus.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.client.AdminWriteGateway;
import com.campus.admin.dto.ConversationRow;
import com.campus.admin.dto.MessageRow;
import com.campus.admin.entity.ContentAudit;
import com.campus.admin.mapper.ChatQueryMapper;
import com.campus.admin.mapper.ContentAuditMapper;
import com.campus.common.search.SearchKit;
import com.campus.common.api.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 互动管理：会话与聊天记录查询、违规消息删除。
 *
 * <p>聊天记录是本项目最敏感的数据。除了网关的统一留痕和 controller 上的
 * {@code @AuditLog}，删除消息时这里还会额外落一条 {@link ContentAudit}，
 * 写清楚「删的是哪条会话里的消息」——删错了要能查回来是谁删的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatQueryMapper chatQueryMapper;
    private final ContentAuditMapper contentAuditMapper;
    private final AdminWriteGateway gateway;

    public R.Page<ConversationRow> pageConversations(String keyword, Long userId, long page, long size) {
        IPage<ConversationRow> p = chatQueryMapper.selectConversations(new Page<>(page, size), SearchKit.words(keyword), userId);
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    public R.Page<MessageRow> pageMessages(Long conversationId, Long senderId, String keyword, long page, long size) {
        IPage<MessageRow> p = chatQueryMapper.selectMessages(new Page<>(page, size), conversationId, senderId, SearchKit.words(keyword));
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Long id, String reason) {
        // 软删后内容对前台不可见，流水里必须先把「哪条会话、谁发给谁」留下来
        MessageRow msg = chatQueryMapper.selectMessage(id);
        gateway.deleteMessage(id, reason);

        ContentAudit audit = new ContentAudit();
        audit.setTargetType("MESSAGE");
        audit.setTargetId(id);
        audit.setAction("DELETE");
        audit.setReasonType("违规内容");
        audit.setReason(reason);
        audit.setSource(1);
        if (msg != null) {
            audit.setAuthorId(msg.getSenderId());
            audit.setAuthorName(msg.getSenderName());
            audit.setTitle("会话 " + msg.getConversationId());
            audit.setContentBrief(msg.getContent() == null ? null
                    : (msg.getContent().length() <= 200 ? msg.getContent() : msg.getContent().substring(0, 200)));
        }
        contentAuditMapper.insert(audit);
    }
}
