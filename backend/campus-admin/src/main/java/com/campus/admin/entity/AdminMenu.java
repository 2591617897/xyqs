package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台菜单与权限点。
 *
 * <p>type=1 目录 / 2 菜单 / 3 按钮。只有带 permission 的行才会参与权限校验，
 * type=2 的菜单带一个 list 权限点表示「能不能进这个页面」，type=3 是其上的按钮动作。
 * component 对应前端 admin-web/src/views 下的 .vue 路径。
 */
@Data
@TableName("t_admin_menu")
public class AdminMenu {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long parentId;

    private String name;

    /** 1目录 2菜单 3按钮 */
    private Integer type;

    private String path;

    private String component;

    private String icon;

    private String permission;

    private Integer sort;

    private Integer visible;

    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
