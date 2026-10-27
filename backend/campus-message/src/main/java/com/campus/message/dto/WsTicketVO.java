package com.campus.message.dto;

import lombok.Data;

/** WebSocket 一次性票据。60 秒内有效、只能用一次，泄露也无害。 */
@Data
public class WsTicketVO {

    private String ticket;

    /** 有效期（秒） */
    private Integer expiresIn;

    /** 连接地址（含 ticket），前端直接 connectSocket 用，避免各端自己拼错 */
    private String url;
}
