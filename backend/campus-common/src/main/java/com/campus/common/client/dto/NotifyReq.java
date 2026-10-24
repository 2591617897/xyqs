package com.campus.common.client.dto;

import lombok.Data;

/**
 * 通知发送请求（Feign 客户端用）。与 campus-message 的 NotifyReq 保持同名字段。
 */
@Data
public class NotifyReq {

    /** LIKE_GOODS / LIKE_POST / LIKE_WALL / NEW_FOLLOWER / COMMENT_POST / COMMENT_WALL / MENTION / SYSTEM */
    private String type;

    /** 通知接收人（必填） */
    private Long receiverId;

    /** 触发者 id（官方通知为空） */
    private Long actorId;

    /** GOODS / POST / WALL / FOCUS / COMMENT */
    private String targetType;

    private Long targetId;

    /** 摘要文本，最多 300 字 */
    private String contentSnapshot;
}
