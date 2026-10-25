package com.campus.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台评论列表行。三类评论（帖子/表白墙/商品）合并展示，用 targetType 区分。
 */
@Data
public class CommentRow {

    private Long id;

    /** P_COMMENT 帖子评论 W_COMMENT 墙评论 G_COMMENT 商品评论 */
    private String targetType;

    private Long targetId;

    /** 被评论内容的标题/摘要 */
    private String targetTitle;

    private Long userId;

    private String userName;

    private String content;

    private String images;

    private LocalDateTime createdAt;
}
