package com.campus.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报工单（与 campus-admin 共享 t_report_ticket 表，各自持有实体以免跨模块依赖）。
 *
 * <p>uk_target_reporter 让同一人对同一内容只能有一条工单，
 * 重复举报由 ReportService 走 UPDATE 累加 reportCount，防止工单表被刷爆。
 */
@Data
@TableName("t_report_ticket")
public class ReportTicket {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String ticketNo;

    /** POST WALL GOODS COMMENT USER MESSAGE FEEDBACK */
    private String targetType;

    private Long targetId;

    private String targetBrief;

    private Long reporterId;

    private String reporterName;

    private String reasonType;

    private String reasonDesc;

    private String images;

    private Integer reportCount;

    /** 0待处理 1已处理 2已驳回 */
    private Integer status;

    private String handleResult;

    private String handleRemark;

    private Long handlerId;

    private String handlerName;

    private Long schoolId;

    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
