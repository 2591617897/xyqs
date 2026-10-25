package com.campus.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改自己密码入参。
 */
@Data
public class ChangePasswordReq {

    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 64, message = "新密码长度需在 8-64 之间")
    private String newPassword;
}
