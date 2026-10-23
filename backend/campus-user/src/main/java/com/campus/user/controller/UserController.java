package com.campus.user.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.user.dto.UpdateProfileReq;
import com.campus.user.dto.UserVO;
import com.campus.user.dto.VerifyReq;
import com.campus.user.dto.VerifyResp;
import com.campus.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(userService.me(UserContext.requireUserId()));
    }

    @PutMapping("/me")
    public R<UserVO> update(@RequestBody UpdateProfileReq req) {
        return R.ok(userService.updateProfile(UserContext.requireUserId(), req));
    }

    /** 提交校园身份认证（学校 + 学号 + 真实姓名 + 学生证照片） */
    @PostMapping("/verify")
    public R<VerifyResp> verify(@RequestBody @Valid VerifyReq req) {
        return R.ok(userService.submitVerify(UserContext.requireUserId(), req));
    }
}
