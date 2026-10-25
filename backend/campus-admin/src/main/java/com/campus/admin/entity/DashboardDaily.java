package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 看板日表（物化统计）。
 *
 * <p>指标分散在 5 个服务的表里，实时聚合会把后台拖垮，
 * 因此每日落一行，看板直接读这张表。schoolId=0 是全平台汇总行。
 */
@Data
@TableName("t_dashboard_daily")
public class DashboardDaily {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private LocalDate statDate;

    private Long schoolId;

    private Integer newUsers;

    private Integer activeUsers;

    private Integer newPosts;

    private Integer newWall;

    private Integer newGoods;

    private Integer newOrders;

    private Integer newMessages;

    private Integer auditCount;

    private Integer reportCount;

    private Integer handledReport;

    private Integer muteCount;

    private Integer banCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
