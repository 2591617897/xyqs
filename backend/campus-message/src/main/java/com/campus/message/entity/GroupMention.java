package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @提醒。只给「被 @ 的那几个人」写行，单条消息最多 10 行。
 *
 * <p>{@code @所有人} 不写这张表 —— 那是万人级写放大，改为更新
 * {@code t_group.last_at_all_msg_id} 一个字段。
 */
@Data
@TableName("t_group_mention")
public class GroupMention {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long groupId;

    private Long messageId;

    /** 被 @ 的人 */
    private Long userId;

    /** 0 未读 1 已读 */
    private Integer readFlag;

    private LocalDateTime createdAt;
}
