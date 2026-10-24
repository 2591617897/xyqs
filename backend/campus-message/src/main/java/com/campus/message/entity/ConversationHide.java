package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话隐藏记录（用户维度）。
 *
 * 左滑删除会话时只写这一行：只有 user_id 本人看不到该会话，
 * 对方侧的会话与消息完全不受影响（消息本体不删）。
 */
@Data
@TableName("t_message_conversation_hide")
public class ConversationHide {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 执行删除的用户（只有他自己看不到该会话） */
    private Long userId;

    /** 被隐藏的会话 id */
    private Long conversationId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
