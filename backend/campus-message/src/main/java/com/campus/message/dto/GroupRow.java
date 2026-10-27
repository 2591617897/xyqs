package com.campus.message.dto;

import lombok.Data;

/** 后台群列表行 */
@Data
public class GroupRow {

    private String id;

    private String name;

    /** 学校名 */
    private String schoolName;

    private Integer memberCount;

    /** 今日消息数 */
    private Integer todayCount;

    private String lastMsgTime;

    private Integer status;

    /** 夜间静音：1 开 0 关 */
    private Integer nightEnabled;

    private String nightRange;

    /** 0 = 自适应 */
    private Integer rateGapSec;

    private Integer rateDayMax;

    private String createdAt;
}
