package com.campus.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜单新增/编辑入参。
 */
@Data
public class AdminMenuReq {

    private Long parentId;

    @NotBlank(message = "请输入菜单名称")
    @Size(max = 64, message = "菜单名称不超过 64 字")
    private String name;

    /** 1目录 2菜单 3按钮 */
    private Integer type;

    @Size(max = 200, message = "路由路径过长")
    private String path;

    @Size(max = 200, message = "组件路径过长")
    private String component;

    @Size(max = 64, message = "图标名过长")
    private String icon;

    @Size(max = 128, message = "权限点字符串过长")
    private String permission;

    private Integer sort;

    private Integer visible;

    private Integer status;

    @Size(max = 255, message = "备注不超过 255 字")
    private String remark;
}
