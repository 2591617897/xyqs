package com.campus.message.dto;

import lombok.Data;

/**
 * 通知发送请求（Feign 内部接口用，不走前端）。
 * type 取值：LIKE_GOODS / LIKE_POST / LIKE_WALL / NEW_FOLLOWER / COMMENT_POST / COMMENT_WALL / MENTION / SYSTEM
 */
@Data
public class NotifyReq {

    private String type;

    /** 通知接收人（必填） */
    private Long receiverId;

    /** 触发者 id（官方通知为空） */
    private Long actorId;

    /** 关联对象类型：GOODS / POST / WALL / FOCUS（关注）/ COMMENT */
    private String targetType;

    /** 关联对象 id：good_id / post_id / wall_post_id / 被关注者 user_id / 评论 id */
    private Long targetId;

    /** 摘要文本（评论内容前 300 字 / 官方文案） */
    private String contentSnapshot;
}
