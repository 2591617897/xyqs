package com.campus.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色新增/编辑入参。
 */
@Data
public class AdminRoleReq {

    @NotBlank(message = "请输入角色名称")
    @Size(max = 64, message = "角色名称不超过 64 字")
    private String name;

    @NotBlank(message = "请输入角色编码")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$", message = "角色编码需为大写字母开头的大写字母/数字/下划线")
    private String code;

    @Size(max = 255, message = "备注不超过 255 字")
    private String remark;

    private Integer sort;

    /** 1启用 0停用 */
    private Integer status;
}
