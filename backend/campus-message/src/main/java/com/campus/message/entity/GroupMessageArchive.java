package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群消息归档（12 个月前）。结构跟热表一致，但去掉了 {@code uk_client}
 * ——归档数据不再接收写入，幂等唯一键纯属浪费空间与写入开销。
 *
 * <p>归档时<b>必须同步迁移 t_group_mention</b>，否则历史 @ 提醒会指向已被删除的消息。
 */
@Data
@TableName("t_group_message_archive")
public class GroupMessageArchive {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long groupId;

    private Long senderId;

    private String senderName;

    private String senderAvatar;

    private String type;

    private String content;

    private Integer atType;

    private String atUserIds;

    /** 被回复的消息 id（归档后原消息可能已不在热表，只作追溯线索） */
    private Long replyToId;

    /** 被回复者昵称快照 */
    private String replyToName;

    /** 被回复内容摘要快照 */
    private String replyToText;

    private Integer deleted;

    /** 删除来源：0 未删除 1 本人撤回 2 管理员删除 */
    private Integer deletedBy;

    private LocalDateTime createdAt;

    private LocalDateTime archivedAt;
}
