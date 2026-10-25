package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台操作审计日志。写多读少，由 {@code AsyncAuditWriter} 批量异步落库。
 */
@Data
@TableName("t_admin_operation_log")
public class AdminOperationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long adminId;

    private String username;

    /** AUTH/CONTENT/USER/AD/REPORT/SYSTEM/NOTICE/CHAT/SENSITIVE */
    private String module;

    private String operation;

    private String method;

    private String uri;

    private String httpMethod;

    private String ip;

    private String ua;

    /** 已脱敏并截断至 2000 字符 */
    private String params;

    /** 0成功 1失败 */
    private Integer result;

    private String errorMsg;

    private Integer costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
