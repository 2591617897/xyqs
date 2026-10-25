package com.campus.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台登录入参。
 */
@Data
public class LoginReq {

    @NotBlank(message = "请输入账号")
    @Size(max = 64, message = "账号长度不正确")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(max = 64, message = "密码长度不正确")
    private String password;
}
