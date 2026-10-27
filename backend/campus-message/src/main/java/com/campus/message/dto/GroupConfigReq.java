package com.campus.message.dto;

import lombok.Data;

/**
 * 后台修改群配置。字段为 null 表示「不改这一项」，
 * 所以不能直接把 null 当成 0 写库 —— Service 里逐项判空。
 */
@Data
public class GroupConfigReq {

    private String name;

    private String notice;

    /** 发言间隔秒，0 = 按群规模自适应 */
    private Integer rateGapSec;

    /** 每日上限，0 = 按群规模自适应 */
    private Integer rateDayMax;

    /** 夜间静音开关 1 开 0 关 */
    private Integer nightEnabled;

    /** HH:mm */
    private String nightStart;

    /** HH:mm */
    private String nightEnd;

    /** 1 正常 0 停用 */
    private Integer status;

    private String reason;
}
