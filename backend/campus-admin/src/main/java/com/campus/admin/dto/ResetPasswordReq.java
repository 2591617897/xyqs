package com.campus.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置他人密码入参。
 */
@Data
public class ResetPasswordReq {

    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 64, message = "新密码长度需在 8-64 之间")
    private String newPassword;
}
