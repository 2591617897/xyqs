package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员-角色关联。
 */
@Data
@TableName("t_admin_user_role")
public class AdminUserRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long adminUserId;

    private Long roleId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
