package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知：点赞 / 关注 / 评论 / @ / 官方。
 * 统一存储在 campus-message 服务，其他服务通过 Feign /internal/notifications 写入。
 */
@Data
@TableName("t_notification")
public class Notification {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** LIKE_GOODS / LIKE_POST / LIKE_WALL / NEW_FOLLOWER / COMMENT_POST / COMMENT_WALL / MENTION / SYSTEM */
    private String type;

    /** 通知接收人 */
    private Long receiverId;

    /** 触发者（官方通知为空） */
    private Long actorId;

    /** GOODS / POST / WALL / FOCUS */
    private String targetType;

    private Long targetId;

    /** 摘要文本（评论内容前 300 字 / 官方文案） */
    private String contentSnapshot;

    /** 0=未读 1=已读 */
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** Enrich 字段：触发者昵称 / 头像（非数据库列，由 Service 层填充） */
    @TableField(exist = false)
    private String actorName;
    @TableField(exist = false)
    private String actorAvatar;
}
