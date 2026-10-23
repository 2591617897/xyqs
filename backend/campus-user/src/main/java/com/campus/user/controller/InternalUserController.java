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
import org.springframework.web.bind.annotation.RestController;

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
}
