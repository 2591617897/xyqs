package com.campus.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台会话列表行。
 */
@Data
public class ConversationRow {

    private Long id;

    private Long userA;

    private Long userB;

    private String userNameA;

    private String userNameB;

    private Long goodsId;

    private String goodsTitle;

    private Integer anon;

    private String lastContent;

    private LocalDateTime lastTime;

    /** 会话内消息条数 */
    private Integer msgCount;

    private Integer status;
}
