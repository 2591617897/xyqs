package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私信消息。read_flag 只标记接收方是否已读（发送方天然已读）。
 */
@Data
@TableName("t_message")
public class Message {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long conversationId;

    private Long senderId;

    private Long receiverId;

    /** text / location / image / system */
    private String type;

    private String content;

    /** 0未读 1已读 */
    private Integer readFlag;

    /** 0正常 1已删除（撤回）：仅发送者本人可删，删后双方都看不到 */
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
