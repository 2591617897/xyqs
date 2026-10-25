package com.campus.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Map;

/**
 * 看板统计查询。
 *
 * <p>一条 SQL 把十几个 count 一次带出来，比发十几个单独 count 少十倍往返。
 * 这里只读、不写任何业务表，也不参与事务。
 */
@Mapper
public interface DashboardQueryMapper {

    /**
     * 总量与今日增量。
     *
     * @param dayStart 今日零点，用于「今日新增」的起点
     */
    @Select("SELECT "
            + " (SELECT COUNT(*) FROM t_user) AS userTotal, "
            + " (SELECT COUNT(*) FROM t_post WHERE status = 1) AS postTotal, "
            + " (SELECT COUNT(*) FROM t_wall_post WHERE status = 1) AS wallTotal, "
            + " (SELECT COUNT(*) FROM t_goods) AS goodsTotal, "
            + " (SELECT COUNT(*) FROM t_goods_order) AS orderTotal, "
            + " (SELECT COUNT(*) FROM t_message WHERE deleted = 0) AS messageTotal, "
            + " (SELECT COUNT(*) FROM t_user WHERE created_at >= #{dayStart}) AS todayUsers, "
            + " (SELECT COUNT(*) FROM t_post WHERE created_at >= #{dayStart}) AS todayPosts, "
            + " (SELECT COUNT(*) FROM t_wall_post WHERE created_at >= #{dayStart}) AS todayWall, "
            + " (SELECT COUNT(*) FROM t_goods WHERE created_at >= #{dayStart}) AS todayGoods, "
            + " (SELECT COUNT(*) FROM t_goods_order WHERE created_at >= #{dayStart}) AS todayOrders, "
            + " (SELECT COUNT(*) FROM t_report_ticket WHERE status = 0) AS pendingReport, "
            + " (SELECT COUNT(*) FROM t_user WHERE verify_status = 1) AS pendingVerify, "
            + " (SELECT COUNT(*) FROM t_user_mute WHERE status = 1) AS mutedNow, "
            + " (SELECT COUNT(*) FROM t_user_blacklist WHERE status = 1) AS bannedNow, "
            + " (SELECT COUNT(*) FROM t_content_audit WHERE created_at >= #{dayStart}) AS todayAudit")
    Map<String, Object> overview(@Param("dayStart") LocalDate dayStart);

    /** 指定日期的日增量，用于回补 t_dashboard_daily */
    @Select("SELECT "
            + " (SELECT COUNT(*) FROM t_user WHERE created_at >= #{from} AND created_at < #{to}) AS newUsers, "
            + " (SELECT COUNT(*) FROM t_post WHERE created_at >= #{from} AND created_at < #{to}) AS newPosts, "
            + " (SELECT COUNT(*) FROM t_wall_post WHERE created_at >= #{from} AND created_at < #{to}) AS newWall, "
            + " (SELECT COUNT(*) FROM t_goods WHERE created_at >= #{from} AND created_at < #{to}) AS newGoods, "
            + " (SELECT COUNT(*) FROM t_goods_order WHERE created_at >= #{from} AND created_at < #{to}) AS newOrders, "
            + " (SELECT COUNT(*) FROM t_message WHERE created_at >= #{from} AND created_at < #{to}) AS newMessages, "
            + " (SELECT COUNT(*) FROM t_content_audit WHERE created_at >= #{from} AND created_at < #{to}) AS auditCount, "
            + " (SELECT COUNT(*) FROM t_report_ticket WHERE created_at >= #{from} AND created_at < #{to}) AS reportCount, "
            + " (SELECT COUNT(*) FROM t_report_ticket WHERE handled_at >= #{from} AND handled_at < #{to}) AS handledReport, "
            + " (SELECT COUNT(*) FROM t_user_mute WHERE created_at >= #{from} AND created_at < #{to}) AS muteCount, "
            + " (SELECT COUNT(*) FROM t_user_blacklist WHERE created_at >= #{from} AND created_at < #{to}) AS banCount")
    Map<String, Object> dailyDelta(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
