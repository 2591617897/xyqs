package com.campus.message.dto;

import lombok.Data;

/** 后台群统计卡 */
@Data
public class GroupStatsVO {

    /** 群总数 */
    private Long groupTotal;

    /** 总成员数（各群 member_count 之和） */
    private Long memberTotal;

    /** 今日消息总数 */
    private Long todayMessages;

    /** 活跃群数：24 小时内有消息 */
    private Long activeGroups;
}
