package com.campus.admin.controller;

import com.campus.admin.client.GroupAdminClient;
import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 校友群管理（后台）。
 *
 * <p>本控制器只做四件事：权限点校验、操作审计、参数归一、透传 campus-message。
 * 业务与数据一致性都在 message 侧（群的归属是那边，后台直接改库会绕过它的缓存与频率闸）。
 *
 * <p>权限点与菜单种子见 {@code backend/sql/migration-20261027-group-chat.sql}。
 */
@Slf4j
@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class AdminGroupController {

    private final GroupAdminClient groupClient;

    // ---------------- 列表 / 统计 ----------------

    @AuditLog(value = "查询校友群列表", module = "GROUP")
    @RequirePermission({"admin:group:list"})
    @GetMapping
    public R<Object> page(@RequestParam(required = false, name = "keyword") String keyword,
                          @RequestParam(required = false, name = "status") Integer status,
                          @RequestParam(defaultValue = "1", name = "page") long page,
                          @RequestParam(defaultValue = "10", name = "size") long size) {
        return unwrap(groupClient.page(keyword, status, page, size));
    }

    /** 统计卡：群总数 / 总成员 / 今日消息 / 活跃群 */
    @RequirePermission({"admin:group:list"})
    @GetMapping("/stats")
    public R<Object> stats() {
        return unwrap(groupClient.stats());
    }

    @AuditLog(value = "查看校友群详情", module = "GROUP")
    @RequirePermission({"admin:group:detail"})
    @GetMapping("/{id}")
    public R<Object> detail(@PathVariable("id") Long id) {
        return unwrap(groupClient.detail(id));
    }

    // ---------------- 群配置（含夜间静音） ----------------

    /**
     * 修改群配置。字段为 null 表示「不改这一项」——
     * 前端没填的项不要补 0，否则会把「自适应」误写成「强制 0 秒」。
     */
    @AuditLog(value = "修改校友群配置", module = "GROUP")
    @RequirePermission({"admin:group:update"})
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> clean = new LinkedHashMap<>();
        // 只透传白名单字段，避免前端顺手带上 id/createdAt 把不该改的列写掉
        String[] allow = {"name", "notice", "rateGapSec", "rateDayMax",
                "nightEnabled", "nightStart", "nightEnd", "status", "reason"};
        for (String k : allow) {
            if (body != null && body.containsKey(k) && body.get(k) != null) {
                clean.put(k, body.get(k));
            }
        }
        if (clean.isEmpty()) {
            throw BizException.of("没有需要修改的字段");
        }
        return unwrapVoid(groupClient.update(id, clean));
    }

    // ---------------- 成员 ----------------

    @AuditLog(value = "查询校友群成员", module = "GROUP")
    @RequirePermission({"admin:group:member:list"})
    @GetMapping("/{id}/members")
    public R<Object> members(@PathVariable("id") Long id,
                             @RequestParam(required = false, name = "keyword") String keyword,
                             @RequestParam(defaultValue = "1", name = "page") long page,
                             @RequestParam(defaultValue = "10", name = "size") long size) {
        return unwrap(groupClient.members(id, keyword, page, size));
    }

    /** 设为 / 取消群管理员（一个群最多 5 个，上限在 message 侧校验） */
    @AuditLog(value = "设置校友群管理员", module = "GROUP")
    @RequirePermission({"admin:group:member:role"})
    @PostMapping("/{id}/members/{userId}/role")
    public R<Void> setRole(@PathVariable("id") Long id, @PathVariable("userId") Long userId,
                           @RequestBody Map<String, Object> body) {
        Integer role = body == null || body.get("role") == null
                ? 0 : Integer.valueOf(String.valueOf(body.get("role")));
        return unwrapVoid(groupClient.setRole(id, userId, Map.of("role", role)));
    }

    /** 禁言：hours = 0 表示永久 */
    @AuditLog(value = "禁言校友群成员", module = "GROUP")
    @RequirePermission({"admin:group:member:mute"})
    @PostMapping("/{id}/mute")
    public R<Void> mute(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        if (body == null || body.get("userId") == null) {
            throw BizException.of("请指定要禁言的用户");
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("userId", String.valueOf(body.get("userId")));
        req.put("hours", body.get("hours") == null ? 24 : Integer.valueOf(String.valueOf(body.get("hours"))));
        req.put("reason", body.get("reason") == null ? "" : String.valueOf(body.get("reason")));
        return unwrapVoid(groupClient.mute(id, req));
    }

    @AuditLog(value = "解除校友群禁言", module = "GROUP")
    @RequirePermission({"admin:group:member:mute"})
    @PostMapping("/{id}/unmute")
    public R<Object> unmute(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        if (body == null || body.get("userId") == null) {
            throw BizException.of("请指定要解除禁言的用户");
        }
        return unwrap(groupClient.unmute(id, Map.of("userId", String.valueOf(body.get("userId")))));
    }

    @AuditLog(value = "踢出校友群成员", module = "GROUP")
    @RequirePermission({"admin:group:member:kick"})
    @PostMapping("/{id}/kick")
    public R<Void> kick(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        if (body == null || body.get("userId") == null) {
            throw BizException.of("请指定要踢出的用户");
        }
        return unwrapVoid(groupClient.kick(id, Map.of("userId", String.valueOf(body.get("userId")))));
    }

    @AuditLog(value = "查询校友群禁言记录", module = "GROUP")
    @RequirePermission({"admin:group:member:mute"})
    @GetMapping("/{id}/mutes")
    public R<Object> mutes(@PathVariable("id") Long id,
                           @RequestParam(defaultValue = "false", name = "activeOnly") boolean activeOnly,
                           @RequestParam(defaultValue = "1", name = "page") long page,
                           @RequestParam(defaultValue = "10", name = "size") long size) {
        return unwrap(groupClient.mutes(id, activeOnly, page, size));
    }

    // ---------------- 消息 ----------------

    @AuditLog(value = "查询校友群消息", module = "GROUP")
    @RequirePermission({"admin:group:message:list"})
    @GetMapping("/{id}/messages")
    public R<Object> messages(@PathVariable("id") Long id,
                              @RequestParam(required = false, name = "keyword") String keyword,
                              @RequestParam(defaultValue = "1", name = "page") long page,
                              @RequestParam(defaultValue = "20", name = "size") long size) {
        return unwrap(groupClient.messages(id, keyword, page, size));
    }

    /** 删除违规消息：软删（deleted=1），与用户撤回写同一列，可追溯 */
    @AuditLog(value = "删除校友群消息", module = "GROUP")
    @RequirePermission({"admin:group:message:delete"})
    @PostMapping("/{id}/messages/{messageId}/delete")
    public R<Void> deleteMessage(@PathVariable("id") Long id, @PathVariable("messageId") Long messageId) {
        return unwrapVoid(groupClient.deleteMessage(id, messageId));
    }

    // ---------------- 内部 ----------------

    private <T> R<T> unwrap(R<T> r) {
        if (r == null) {
            throw BizException.of("校友群服务无响应，请稍后重试");
        }
        if (r.getCode() != 0) {
            throw BizException.of(r.getMsg() == null ? "校友群服务返回异常" : r.getMsg());
        }
        return r;
    }

    private R<Void> unwrapVoid(R<Void> r) {
        unwrap(r);
        return R.ok();
    }
}
