package com.campus.message.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 聊天记录里的一条消息 */
@Data
public class MessageVO {

    private Long id;

    /** me 我发的 / peer 对方发的 */
    private String from;

    /** text / location / image / system */
    private String type;

    private String content;

    private LocalDateTime createdAt;
}
