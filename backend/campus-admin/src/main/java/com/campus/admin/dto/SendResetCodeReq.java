package com.campus.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请找回密码验证码。收件邮箱不由前端提供 —— 只发库里已绑定的那个。
 */
@Data
public class SendResetCodeReq {

    @NotBlank(message = "请输入管理员账号")
    @Size(max = 32, message = "账号长度不合法")
    private String username;
}
