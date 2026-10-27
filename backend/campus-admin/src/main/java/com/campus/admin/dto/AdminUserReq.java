package com.campus.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理员新增/编辑入参。password 为空表示不修改密码（编辑场景）。
 */
@Data
public class AdminUserReq {

    @NotBlank(message = "请输入账号")
    @Size(min = 4, max = 32, message = "账号长度需在 4-32 之间")
    private String username;

    /**
     * 编辑场景允许留空（表示不修改），所以这里不能用 {@code @Size(min = 8)}：
     * {@code @Size} 只放过 null，前端传空串 "" 会被判成「长度不足 8」，
     * 请求在进 Service 之前就被 400 拦掉，根本走不到「留空不修改」的分支。
     * 用 @Pattern 显式把空串和 8-64 位都放行，真正的强度校验交给
     * {@code AdminPasswordUtil#checkStrength}。
     */
    @Pattern(regexp = "^$|^[\\s\\S]{8,64}$", message = "密码长度需在 8-64 之间")
    private String password;

    @Size(max = 32, message = "姓名不超过 32 字")
    private String realName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 64, message = "邮箱不超过 64 字符")
    private String email;

    /** 1全部 2仅辖区 */
    private Integer dataScope;

    /** 1正常 0禁用 */
    private Integer status;

    @Size(max = 255, message = "备注不超过 255 字")
    private String remark;

    @NotEmpty(message = "请至少选择一个角色")
    private List<Long> roleIds;
}
