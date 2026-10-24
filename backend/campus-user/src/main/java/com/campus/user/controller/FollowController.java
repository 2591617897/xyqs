package com.campus.user.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.user.dto.FollowResp;
import com.campus.user.dto.FollowUserVO;
import com.campus.user.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 关注 / 取消关注 / 关注状态 / 关注列表。
 * POST / DELETE / GET list 必须登录；GET status 未登录返回 false，不报 401。
 */
@RestController
@RequestMapping("/user/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /** 关注。幂等：已关注直接成功不重复计数 */
    @PostMapping("/{userId}")
    public R<FollowResp> follow(@PathVariable("userId") Long userId) {
        Long followerId = UserContext.requireUserId();
        return R.ok(followService.follow(followerId, userId));
    }

    /** 取消关注。未关注也返回成功 */
    @DeleteMapping("/{userId}")
    public R<FollowResp> unfollow(@PathVariable("userId") Long userId) {
        Long followerId = UserContext.requireUserId();
        return R.ok(followService.unfollow(followerId, userId));
    }

    /** 关注状态：true = 已关注；未登录返回 false */
    @GetMapping("/status")
    public R<Boolean> status(@RequestParam("userId") Long userId) {
        Long followerId = UserContext.userId();
        return R.ok(followService.status(followerId, userId));
    }

    /**
     * 关注列表（我关注的 / 关注我的）。
     * type=following → 我关注的人；type=fans → 关注我的人。默认 following。
     */
    @GetMapping("/list")
    public R<List<FollowUserVO>> list(@RequestParam(defaultValue = "following") String type) {
        Long me = UserContext.requireUserId();
        return R.ok(followService.list(me, type));
    }
}
