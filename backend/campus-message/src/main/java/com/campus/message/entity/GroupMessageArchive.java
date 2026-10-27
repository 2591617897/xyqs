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

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime archivedAt;
}
