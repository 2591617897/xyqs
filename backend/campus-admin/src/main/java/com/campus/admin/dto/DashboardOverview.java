package com.campus.admin.dto;

import lombok.Data;

/**
 * 后台首页概览。总量与今日增量分开，避免前端自己算差值算错。
 */
@Data
public class DashboardOverview {

    // 总量
    private long userTotal;
    private long postTotal;
    private long wallTotal;
    private long goodsTotal;
    private long orderTotal;
    private long messageTotal;

    // 今日新增
    private long todayUsers;
    private long todayPosts;
    private long todayWall;
    private long todayGoods;
    private long todayOrders;

    // 待办与在处置中
    private long pendingReport;
    private long pendingVerify;
    private long mutedNow;
    private long bannedNow;
    private long todayAudit;
}
