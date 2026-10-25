package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容处置流水。
 *
 * <p>只记录「谁在什么时候对什么内容做了什么」，不承载内容的最终态——
 * 帖子/墙/商品的可见性仍以各自表的 status 为准，避免出现两个真相。
 * 匿名内容（表白墙）在这里留存 authorId，前台展示依旧匿名，
 * 「前台匿名 + 后台可追溯」由这一行承担。
 */
@Data
@TableName("t_content_audit")
public class ContentAudit {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** POST帖子 WALL表白墙 GOODS商品 P_COMMENT W_COMMENT G_COMMENT */
    private String targetType;

    private Long targetId;

    /** 内容快照：原内容被物理删除后仍可追溯 */
    private String title;

    private String contentBrief;

    private String images;

    private Long authorId;

    private String authorName;

    private Long schoolId;

    /** HIDE隐藏 SHOW恢复 DELETE删除 REJECT驳回 PASS通过 WARN警告 */
    private String action;

    private String reasonType;

    private String reason;

    private Long adminId;

    private String adminName;

    /** 1后台人工 2系统自动（敏感词） 3举报联动 */
    private Integer source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
