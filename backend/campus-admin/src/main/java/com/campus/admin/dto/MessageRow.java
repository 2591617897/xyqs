package com.campus.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台消息列表行。聊天记录查询是敏感操作，命中即写操作日志（见 ChatService）。
 */
@Data
public class MessageRow {

    private Long id;

    private Long conversationId;

    private Long senderId;

    private String senderName;

    private Long receiverId;

    private String receiverName;

    private String type;

    private String content;

    private Integer readFlag;

    private Integer deleted;

    private LocalDateTime createdAt;
}
