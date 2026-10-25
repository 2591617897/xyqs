package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台登录日志，成功与失败都记录。
 */
@Data
@TableName("t_admin_login_log")
public class AdminLoginLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private Long adminId;

    private String ip;

    private String ua;

    /** 1成功 0失败 */
    private Integer status;

    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
