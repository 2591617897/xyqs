package com.campus.message.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.message.dto.ConversationVO;
import com.campus.message.dto.MessageCenterVO;
import com.campus.message.dto.MessageVO;
import com.campus.message.dto.SendMessageReq;
import com.campus.message.dto.StartConversationReq;
import com.campus.message.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 私信与消息中心。全部接口需要登录（网关 /api/messages/** 不在公开读白名单内）。
 */
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 消息中心：统计 + 会话列表 */
    @GetMapping
    public R<MessageCenterVO> center() {
        Long userId = UserContext.requireUserId();
        MessageCenterVO vo = new MessageCenterVO();
        vo.setSummary(messageService.summary(userId));
        vo.setConvos(messageService.conversations(userId));
        return R.ok(vo);
    }

    /** 未读消息总数（首页铃铛角标） */
    @GetMapping("/unread")
    public R<Map<String, Object>> unread() {
        return R.ok(Map.of("unread", messageService.unreadTotal(UserContext.requireUserId())));
    }

    /**
     * 发起或复用会话。前端从商品详情「聊一聊」进入时调用，
     * 拿到 conversation id 后再跳聊天页。
     */
    @PostMapping("/start")
    public R<ConversationVO> start(@RequestBody @Valid StartConversationReq req) {
        return R.ok(messageService.start(UserContext.requireUserId(), req));
    }

    /** 会话详情（含聊天记录），进入即把对方消息标为已读 */
    @GetMapping("/{id}")
    public R<ConversationVO> detail(@PathVariable("id") Long id) {
        return R.ok(messageService.detail(id, UserContext.requireUserId()));
    }

    /**
     * 发消息。返回整个会话的最新聊天记录，便于前端一次性对齐本地气泡。
     */
    @PostMapping("/{id}")
    public R<ConversationVO> send(@PathVariable("id") Long id, @RequestBody @Valid SendMessageReq req) {
        return R.ok(messageService.send(id, UserContext.requireUserId(), req));
    }

    /** 标记整个会话为已读 */
    @PostMapping("/{id}/read")
    public R<Void> read(@PathVariable("id") Long id) {
        messageService.read(id, UserContext.requireUserId());
        return R.ok();
    }

    /** 只取聊天记录（不触发已读），用于刷新 */
    @GetMapping("/{id}/messages")
    public R<List<MessageVO>> messages(@PathVariable("id") Long id) {
        Long userId = UserContext.requireUserId();
        messageService.requireMember(id, userId);
        return R.ok(messageService.messages(id, userId));
    }
}
