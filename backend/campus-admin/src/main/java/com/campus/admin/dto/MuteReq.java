package com.campus.admin.dto;

import lombok.Data;

/**
 * 禁言入参。minutes=0 表示永久（内部写成 100 年后，避免与「未禁言」的 NULL 混淆）。
 */
@Data
public class MuteReq {

    /** 1全站 2发帖 3评论 4私信，默认 1 */
    private Integer scope;

    /** 禁言时长（分钟），0 或空表示永久 */
    private Integer minutes;

    private String reasonType;

    private String reason;
}
