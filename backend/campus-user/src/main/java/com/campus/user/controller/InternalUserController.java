package com.campus.user.controller;

import com.campus.common.api.R;
import com.campus.user.dto.LoginOrCreateReq;
import com.campus.user.dto.UserBrief;
import com.campus.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部接口：只允许服务间调用，网关不路由 /internal/**。
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @PostMapping("/login-or-create")
    public R<UserBrief> loginOrCreate(@RequestBody LoginOrCreateReq req) {
        return R.ok(userService.loginOrCreate(req));
    }

    @GetMapping("/{id}")
    public R<UserBrief> get(@PathVariable("id") Long id) {
        return R.ok(userService.brief(id));
    }

    /**
     * 账号准入状态（封禁 + 令牌版本），网关鉴权链路专用。
     *
     * <p>网关在每次校验完 JWT 后会调它，并做 60 秒内存缓存：
     * <ul>
     *   <li>{@code status=0} → 封禁，网关返回 403；</li>
     *   <li>{@code tokenVersion} 与令牌里的 {@code tv} 不一致 → 注销 / 解封后的旧令牌，返回 401；</li>
     *   <li>用户不存在 → 返回 {@code data:null}，网关同样判失效。</li>
     * </ul>
     *
     * <p><b>不要把这条路径挪到 /api 下</b>：它能让调用方知道任意 userId 是否被封禁，
     * 只能走服务间内网调用，网关不路由 /internal/**。
     */
    @GetMapping("/{id}/auth-state")
    public R<UserBrief> authState(@PathVariable("id") Long id) {
        return R.ok(userService.authState(id));
    }

    /** 批量摘要：GET /internal/users/briefs?ids=1,2,3 */
    @GetMapping("/briefs")
    public R<List<UserBrief>> briefs(@RequestParam(name = "ids") List<Long> ids) {
        return R.ok(userService.briefs(ids));
    }
}
