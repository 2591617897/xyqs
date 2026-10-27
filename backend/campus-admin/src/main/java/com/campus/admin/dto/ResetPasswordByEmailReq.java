package com.campus.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用邮箱验证码重置密码。
 *
 * <p>不做 @Size(min = 8) 之类的长度课税：强度规则统一走
 * {@code AdminPasswordUtil#checkStrength}，那里还要判弱口令、三类字符、是否含账号名，
 * 校验分散在注解里只会出现两套口径、两套提示文案。
 */
@Data
public class ResetPasswordByEmailReq {

    @NotBlank(message = "请输入管理员账号")
    @Size(max = 32, message = "账号长度不合法")
    private String username;

    @NotBlank(message = "请输入验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String code;

    @NotBlank(message = "请输入新密码")
    private String newPassword;
}
