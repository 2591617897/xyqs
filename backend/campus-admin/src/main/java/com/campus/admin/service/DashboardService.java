package com.campus.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.admin.dto.DashboardOverview;
import com.campus.admin.entity.DashboardDaily;
import com.campus.admin.mapper.DashboardDailyMapper;
import com.campus.admin.mapper.DashboardQueryMapper;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据看板。
 *
 * <p>「概览」是实时聚合：十几个 count 凑成一条 SQL，量不大时够快。
 * 「日表」是物化结果：趋势图不能每次扫全表，所以按天落一行。
 * 两者数据源一致，日表由后台点「重算指标」或定时任务回补，
 * 重算走 uk_date_school 做 upsert，重复点不会插出两行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardQueryMapper dashboardQueryMapper;
    private final DashboardDailyMapper dashboardDailyMapper;

    public DashboardOverview overview() {
        Map<String, Object> m = dashboardQueryMapper.overview(LocalDate.now());
        DashboardOverview vo = new DashboardOverview();
        vo.setUserTotal(num(m, "userTotal"));
        vo.setPostTotal(num(m, "postTotal"));
        vo.setWallTotal(num(m, "wallTotal"));
        vo.setGoodsTotal(num(m, "goodsTotal"));
        vo.setOrderTotal(num(m, "orderTotal"));
        vo.setMessageTotal(num(m, "messageTotal"));
        vo.setTodayUsers(num(m, "todayUsers"));
        vo.setTodayPosts(num(m, "todayPosts"));
        vo.setTodayWall(num(m, "todayWall"));
        vo.setTodayGoods(num(m, "todayGoods"));
        vo.setTodayOrders(num(m, "todayOrders"));
        vo.setPendingReport(num(m, "pendingReport"));
        vo.setPendingVerify(num(m, "pendingVerify"));
        vo.setMutedNow(num(m, "mutedNow"));
        vo.setBannedNow(num(m, "bannedNow"));
        vo.setTodayAudit(num(m, "todayAudit"));
        return vo;
    }

    public R.Page<DashboardDaily> pageDaily(LocalDate from, LocalDate to, long page, long size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw BizException.of("开始日期不能晚于结束日期");
        }
        List<DashboardDaily> list = dashboardDailyMapper.selectList(Wrappers.<DashboardDaily>lambdaQuery()
                .ge(from != null, DashboardDaily::getStatDate, from)
                .le(to != null, DashboardDaily::getStatDate, to)
                .orderByDesc(DashboardDaily::getStatDate)
                .last("LIMIT " + (page - 1) * size + ", " + size));
        long total = dashboardDailyMapper.selectCount(Wrappers.<DashboardDaily>lambdaQuery()
                .ge(from != null, DashboardDaily::getStatDate, from)
                .le(to != null, DashboardDaily::getStatDate, to));
        return R.Page.of(list, total, page, size);
    }

    /**
     * 重算某天的日表（全平台汇总行，school_id=0）。
     *
     * @return 1 表示新增，2 表示更新
     */
    @Transactional(rollbackFor = Exception.class)
    public int rebuild(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now().minusDays(1) : date;
        Map<String, Object> d = dashboardQueryMapper.dailyDelta(day, day.plusDays(1));

        DashboardDaily exist = dashboardDailyMapper.selectOne(Wrappers.<DashboardDaily>lambdaQuery()
                .eq(DashboardDaily::getStatDate, day)
                .eq(DashboardDaily::getSchoolId, 0L));

        DashboardDaily row = exist == null ? new DashboardDaily() : exist;
        row.setStatDate(day);
        row.setSchoolId(0L);
        row.setNewUsers(num(d, "newUsers"));
        row.setNewPosts(num(d, "newPosts"));
        row.setNewWall(num(d, "newWall"));
        row.setNewGoods(num(d, "newGoods"));
        row.setNewOrders(num(d, "newOrders"));
        row.setNewMessages(num(d, "newMessages"));
        row.setAuditCount(num(d, "auditCount"));
        row.setReportCount(num(d, "reportCount"));
        row.setHandledReport(num(d, "handledReport"));
        row.setMuteCount(num(d, "muteCount"));
        row.setBanCount(num(d, "banCount"));

        if (exist == null) {
            dashboardDailyMapper.insert(row);
            return 1;
        }
        dashboardDailyMapper.updateById(row);
        return 2;
    }

    private int num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? 0 : ((Number) v).intValue();
    }
}
