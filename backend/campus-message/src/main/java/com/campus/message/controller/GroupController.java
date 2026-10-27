package com.campus.message.controller;

import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.common.security.UserContext;
import com.campus.message.dto.GroupCenterVO;
import com.campus.message.dto.GroupDetailVO;
import com.campus.message.dto.GroupMemberVO;
import com.campus.message.dto.GroupMsgVO;
import com.campus.message.dto.NotifyModeReq;
import com.campus.message.dto.ReadReq;
import com.campus.message.dto.SendGroupMsgReq;
import com.campus.message.dto.WsTicketVO;
import com.campus.message.service.GroupService;
import com.campus.message.ws.WsTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 校友群 C 端接口。全部需要登录（网关 {@code /api/groups/**} 不在公开读白名单里）。
 *
 * <p>HTTP 通道承担三件事：拉历史 / 发消息兜底 / 轮询兜底。
 * 实时主通道是 WebSocket（{@code /api/ws}），两条通道共用同一套 {@code sinceId} 语义。
 */
@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final WsTicketService ticketService;

    /**
     * 换一次性 WebSocket 票据（60 秒、用一次即废）。
     *
     * <p>不把 JWT 直接拼进 ws URL：Nginx access_log 会记录完整 URL，
     * 72 小时有效的令牌落进日志等于泄露。
     */
    @PostMapping("/ws-ticket")
    public R<WsTicketVO> wsTicket() {
        return R.ok(ticketService.issue(UserContext.requireUserId()));
    }

    /** 我的群卡片（未绑定学校时返回 needSchool=true，前端引导去选学校） */
    @GetMapping("/mine")
    public R<GroupCenterVO> mine() {
        return R.ok(groupService.mine(UserContext.requireUserId(), UserContext.schoolId()));
    }

    @GetMapping("/{id}")
    public R<GroupDetailVO> detail(@PathVariable("id") Long id) {
        return R.ok(groupService.detail(id, UserContext.requireUserId(), UserContext.schoolId()));
    }

    /**
     * 拉消息。
     *
     * @param sinceId  增量补拉（WebSocket 重连后必调，否则断线期间的消息永久丢失）
     * @param beforeId 上滑翻历史
     * @param limit    默认 30，服务端硬上限 50
     */
    @GetMapping("/{id}/messages")
    public R<List<GroupMsgVO>> messages(@PathVariable("id") Long id,
                                        @RequestParam(value = "sinceId", required = false) String sinceId,
                                        @RequestParam(value = "before", required = false) String before,
                                        @RequestParam(value = "limit", required = false) Integer limit) {
        return R.ok(groupService.messages(id, UserContext.requireUserId(),
                toLong(sinceId), toLong(before), limit));
    }

    /** 发消息（WebSocket 不可用时的兜底通道，语义与 WS 完全一致） */
    @PostMapping("/{id}/messages")
    public R<GroupMsgVO> send(@PathVariable("id") Long id, @RequestBody SendGroupMsgReq req) {
        return R.ok(groupService.send(id, UserContext.requireUserId(), req));
    }

    /**
     * 撤回自己发的消息（发送后 2 分钟内，与微信一致）。
     *
     * <p>走 HTTP 而不是 WebSocket：撤回是一次性的强一致写操作，
     * 需要明确的成功/失败回执（HTTP 状态 + 业务码），
     * 让前端能把「超过 2 分钟无法撤回」原样提示给用户。
     */
    @PostMapping("/{id}/messages/{msgId}/recall")
    public R<Void> recall(@PathVariable("id") Long id, @PathVariable("msgId") Long msgId) {
        groupService.recall(id, UserContext.requireUserId(), msgId);
        return R.ok();
    }

    /** 上报已读游标（同时清 @我 的未读）；不传 lastReadId 视为「全部已读」到最新 */
    @PostMapping("/{id}/read")
    public R<Void> read(@PathVariable("id") Long id, @RequestBody(required = false) ReadReq req) {
        Long lastRead = req == null ? null : toLong(req.getLastReadId());
        if (lastRead == null) {
            // 前端只发 {} 表示「全部已读」：取群最新消息 id 作为游标
            lastRead = groupService.latestMessageId(id);
        }
        groupService.read(id, UserContext.requireUserId(), lastRead);
        return R.ok();
    }

    /** 确认群规（首次进群引导层）；存后端不存本地：换手机不该重复弹，且这是告知确认的证据 */
    @PostMapping("/{id}/agree-rules")
    public R<Void> agreeRules(@PathVariable("id") Long id) {
        groupService.agreeRules(id, UserContext.requireUserId());
        return R.ok();
    }

    /** 成员列表（@ 选择器；一万人必须支持 keyword 搜索） */
    @GetMapping("/{id}/members")
    public R<R.Page<GroupMemberVO>> members(@PathVariable("id") Long id,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "page", defaultValue = "1") long page,
                                            @RequestParam(value = "size", defaultValue = "50") long size) {
        return R.ok(groupService.members(id, UserContext.requireUserId(), keyword, page, size));
    }

    /** 退群后重新加入 */
    @PostMapping("/{id}/join")
    public R<Map<String, Object>> join(@PathVariable("id") Long id) {
        groupService.rejoin(id, UserContext.requireUserId());
        return R.ok(Map.of("joined", true));
    }

    /** 主动退群：保留成员记录（status=2），之后不会被自动入群拉回来 */
    @PostMapping("/{id}/quit")
    public R<Void> quit(@PathVariable("id") Long id) {
        groupService.quit(id, UserContext.requireUserId());
        return R.ok();
    }

    /** 提醒模式：0 仅 @ 提醒（默认） 1 全部消息 2 完全静音 */
    @PutMapping("/{id}/notify")
    public R<Void> notify(@PathVariable("id") Long id, @RequestBody @Valid NotifyModeReq req) {
        groupService.setNotifyMode(id, UserContext.requireUserId(), req.getNotifyMode());
        return R.ok();
    }

    private Long toLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw BizException.of("id 参数格式不正确");
        }
    }
}
