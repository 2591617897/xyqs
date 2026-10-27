package com.campus.mail.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发信日志。字段名不用 {@code to}：那是 MySQL 保留字，会让每条 SQL 都得手写反引号。
 */
@Data
@TableName("t_mail_log")
public class MailLog {

    /** 0 待发 1 成功 2 失败 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_FAILED = 2;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 幂等键，唯一索引 */
    private String requestId;

    private String bizType;

    private String bizId;

    private String recipient;

    private String template;

    private String subject;

    private Integer status;

    private Integer retryCount;

    private String errorMsg;

    private LocalDateTime sentAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
