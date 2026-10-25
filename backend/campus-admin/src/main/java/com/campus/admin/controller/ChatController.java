package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.ConversationRow;
import com.campus.admin.dto.MessageRow;
import com.campus.admin.dto.ReasonReq;
import com.campus.admin.service.ChatService;
import com.campus.common.api.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 互动管理：会话与聊天记录查询、违规消息删除。
 *
 * <p>消息列表是本项目敏感度最高的接口，所以即使只是「看」也带
 * {@code @AuditLog} —— 谁在什么时候翻了谁的聊天，必须有据可查。
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @AuditLog(value = "查询会话列表", module = "CHAT")
    @RequirePermission({"admin:chat:conversation:list"})
    @GetMapping("/conversations")
    public R<R.Page<ConversationRow>> pageConversations(
            @RequestParam(required = false, name = "keyword") String keyword,
            @RequestParam(required = false, name = "userId") Long userId,
            @RequestParam(defaultValue = "1", name = "page") long page,
            @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(chatService.pageConversations(keyword, userId, page, size));
    }

    @AuditLog(value = "查询聊天记录", module = "CHAT")
    @RequirePermission({"admin:chat:message:list"})
    @GetMapping("/messages")
    public R<R.Page<MessageRow>> pageMessages(
            @RequestParam(required = false, name = "conversationId") Long conversationId,
            @RequestParam(required = false, name = "senderId") Long senderId,
            @RequestParam(required = false, name = "keyword") String keyword,
            @RequestParam(defaultValue = "1", name = "page") long page,
            @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(chatService.pageMessages(conversationId, senderId, keyword, page, size));
    }

    @AuditLog(value = "删除违规消息", module = "CHAT")
    @RequirePermission({"admin:chat:message:delete"})
    @PostMapping("/messages/{id}/delete")
    public R<Void> deleteMessage(@PathVariable("id") Long id, @RequestBody @Valid ReasonReq req) {
        chatService.deleteMessage(id, req.getReason());
        return R.ok();
    }
}
