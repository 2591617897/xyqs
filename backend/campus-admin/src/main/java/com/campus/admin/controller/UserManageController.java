package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.AuditReq;
import com.campus.admin.dto.MuteReq;
import com.campus.admin.dto.ReasonReq;
import com.campus.admin.dto.UserRow;
import com.campus.admin.dto.UserStats;
import com.campus.admin.entity.UserBlacklist;
import com.campus.admin.entity.UserMute;
import com.campus.admin.entity.UserVerifyLog;
import com.campus.admin.service.UserManageService;
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
 * 用户管理：列表、详情、封禁、禁言、黑名单、实名审核。
 *
 * <p>路径用 /users（后台自己的资源命名），与小程序 /api/user/** 无关。
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserManageController {

    private final UserManageService userManageService;

    @RequirePermission({"admin:user:list"})
    @GetMapping
    public R<R.Page<UserRow>> page(@RequestParam(required = false, name = "keyword") String keyword,
                                   @RequestParam(required = false, name = "schoolId") Long schoolId,
                                   @RequestParam(required = false, name = "verifyStatus") Integer verifyStatus,
                                   @RequestParam(required = false, name = "status") Integer status,
                                   @RequestParam(required = false, name = "muted") Integer muted,
                                   @RequestParam(required = false, name = "blacklisted") Integer blacklisted,
                                   @RequestParam(defaultValue = "1", name = "page") long page,
                                   @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(userManageService.pageUsers(keyword, schoolId, verifyStatus, status, muted, blacklisted, page, size));
    }

    @RequirePermission({"admin:user:detail"})
    @GetMapping("/{id}")
    public R<UserRow> detail(@PathVariable("id") Long id) {
        return R.ok(userManageService.userDetail(id));
    }

    @RequirePermission({"admin:user:detail"})
    @GetMapping("/{id}/stats")
    public R<UserStats> stats(@PathVariable("id") Long id) {
        return R.ok(userManageService.userStats(id));
    }

    // ---------------- 封禁 / 黑名单 ----------------

    @AuditLog(value = "封禁用户", module = "USER")
    @RequirePermission({"admin:user:ban"})
    @PostMapping("/{id}/ban")
    public R<Void> ban(@PathVariable("id") Long id, @RequestBody @Valid ReasonReq req) {
        userManageService.ban(id, null, req.getReason());
        return R.ok();
    }

    @AuditLog(value = "解除封禁", module = "USER")
    @RequirePermission({"admin:user:ban"})
    @PostMapping("/{id}/unban")
    public R<Void> unban(@PathVariable("id") Long id) {
        userManageService.unban(id);
        return R.ok();
    }

    @RequirePermission({"admin:user:blacklist:list"})
    @GetMapping("/blacklist")
    public R<R.Page<UserBlacklist>> pageBlacklist(@RequestParam(required = false, name = "keyword") String keyword,
                                                  @RequestParam(defaultValue = "1", name = "page") long page,
                                                  @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(userManageService.pageBlacklist(keyword, page, size));
    }

    @AuditLog(value = "拉黑用户", module = "USER")
    @RequirePermission({"admin:user:blacklist"})
    @PostMapping("/{id}/blacklist")
    public R<Void> blacklist(@PathVariable("id") Long id, @RequestBody @Valid ReasonReq req) {
        userManageService.ban(id, null, req.getReason());
        return R.ok();
    }

    @AuditLog(value = "解除拉黑", module = "USER")
    @RequirePermission({"admin:user:blacklist"})
    @PostMapping("/{id}/blacklist/release")
    public R<Void> releaseBlacklist(@PathVariable("id") Long id) {
        userManageService.unban(id);
        return R.ok();
    }

    // ---------------- 禁言 ----------------

    @AuditLog(value = "禁言用户", module = "USER")
    @RequirePermission({"admin:user:mute"})
    @PostMapping("/{id}/mute")
    public R<Void> mute(@PathVariable("id") Long id, @RequestBody @Valid MuteReq req) {
        userManageService.mute(id, req.getScope(), req.getMinutes(), req.getReasonType(), req.getReason());
        return R.ok();
    }

    @AuditLog(value = "解除禁言", module = "USER")
    @RequirePermission({"admin:user:mute"})
    @PostMapping("/{id}/unmute")
    public R<Void> unmute(@PathVariable("id") Long id,
                          @RequestParam(required = false, name = "scope") Integer scope) {
        userManageService.unmute(id, scope);
        return R.ok();
    }

    @RequirePermission({"admin:user:mute:list"})
    @GetMapping("/mutes")
    public R<R.Page<UserMute>> pageMutes(@RequestParam(required = false, name = "userId") Long userId,
                                         @RequestParam(defaultValue = "1", name = "page") long page,
                                         @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(userManageService.pageMutes(userId, page, size));
    }

    /** 手动触发到期禁言的解除扫描（正常由定时任务调用，这里留个应急入口） */
    @AuditLog(value = "扫描到期禁言", module = "USER")
    @RequirePermission({"admin:user:mute"})
    @PostMapping("/mutes/release-expired")
    public R<Integer> releaseExpiredMutes() {
        return R.ok(userManageService.releaseExpiredMutes());
    }

    // ---------------- 实名审核 ----------------

    @RequirePermission({"admin:user:verify:list"})
    @GetMapping("/verify/pending")
    public R<R.Page<UserRow>> pageVerifyPending(@RequestParam(defaultValue = "1", name = "page") long page,
                                                @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(userManageService.pageVerifyPending(page, size));
    }

    @AuditLog(value = "实名认证审核", module = "USER")
    @RequirePermission({"admin:user:verify"})
    @PostMapping("/{id}/verify")
    public R<Void> verify(@PathVariable("id") Long id, @RequestBody @Valid AuditReq req) {
        if (req.getPass() == null) {
            throw new IllegalArgumentException("pass 不能为空");
        }
        userManageService.verify(id, req.getPass(), req.getReason());
        return R.ok();
    }

    @RequirePermission({"admin:user:verify:list"})
    @GetMapping("/verify/logs")
    public R<R.Page<UserVerifyLog>> pageVerifyLogs(@RequestParam(required = false, name = "userId") Long userId,
                                                   @RequestParam(required = false, name = "toStatus") Integer toStatus,
                                                   @RequestParam(defaultValue = "1", name = "page") long page,
                                                   @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(userManageService.pageVerifyLogs(userId, toStatus, page, size));
    }
}
