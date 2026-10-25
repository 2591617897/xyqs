package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台角色。
 */
@Data
@TableName("t_admin_role")
public class AdminRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    /** 角色编码，如 SUPER_ADMIN / CONTENT_AUDITOR */
    private String code;

    private String remark;

    private Integer sort;

    /** 1启用 0停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
