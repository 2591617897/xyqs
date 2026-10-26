package com.campus.promote.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.promote.service.PromoteService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推广接口（全部需要登录）。
 *
 * <p>链路：quota 看剩余次数 → issue 拿凭证 → 小程序播激励视频 → complete 结算。
 * 中途放弃或广告加载失败走 cancel 释放配额。
 */
@Validated
@RestController
@RequestMapping("/promote")
@RequiredArgsConstructor
public class PromoteController {

    private final PromoteService promoteService;

    /** 当前推广分与今日剩余次数（进推广弹层时先拉一次） */
    @GetMapping("/quota")
    public R<PromoteService.QuotaVO> quota(@RequestParam("targetType") String targetType,
                                           @RequestParam("targetId") Long targetId) {
        return R.ok(promoteService.quota(UserContext.requireUserId(), targetType, targetId));
    }

    /** 签发看广告凭证（此时不加分） */
    @PostMapping("/issue")
    public R<PromoteService.IssueVO> issue(@RequestBody @Validated IssueReq req) {
        return R.ok(promoteService.issue(UserContext.requireUserId(), req.getTargetType(), req.getTargetId()));
    }

    /** 看完整段视频后结算 */
    @PostMapping("/complete")
    public R<PromoteService.CompleteVO> complete(@RequestBody @Validated TokenReq req) {
        return R.ok(promoteService.complete(UserContext.requireUserId(), req.getToken()));
    }

    /** 放弃本次推广（释放当日配额） */
    @PostMapping("/cancel")
    public R<Void> cancel(@RequestBody @Validated TokenReq req) {
        promoteService.cancel(UserContext.requireUserId(), req.getToken());
        return R.ok();
    }

    /** 手动触发一次衰减重算（排障/演示用，不对外暴露开关，只回写分数） */
    @PostMapping("/refresh")
    public R<Integer> refresh() {
        UserContext.requireUserId();
        return R.ok(promoteService.refreshExpired());
    }

    @Data
    public static class IssueReq {

        @NotBlank(message = "targetType 不能为空")
        private String targetType;

        @NotNull(message = "targetId 不能为空")
        private Long targetId;
    }

    @Data
    public static class TokenReq {

        @NotBlank(message = "推广凭证不能为空")
        private String token;
    }
}
