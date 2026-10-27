package com.campus.message.dto;

import lombok.Data;

/**
 * 上报已读游标。传的是「我已经看到的最新消息 id」，
 * 服务端只前进不后退（多端同时看群时，旧游标不能把进度倒退）。
 */
@Data
public class ReadReq {

    private String lastReadId;
}
