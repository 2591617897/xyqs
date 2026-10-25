package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.DashboardOverview;
import com.campus.admin.entity.DashboardDaily;
import com.campus.admin.service.DashboardService;
import com.campus.common.api.R;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 数据看板：实时概览 + 物化日表。
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @RequirePermission({"admin:dashboard:view"})
    @GetMapping("/overview")
    public R<DashboardOverview> overview() {
        return R.ok(dashboardService.overview());
    }

    @RequirePermission({"admin:dashboard:view"})
    @GetMapping("/daily")
    public R<R.Page<DashboardDaily>> pageDaily(
            @RequestParam(required = false, name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false, name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1", name = "page") long page,
            @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(dashboardService.pageDaily(from, to, page, size));
    }

    /**
     * 重算某天的日表。默认重算昨天（今天还没过完，数字会一直变）。
     */
    @AuditLog(value = "重算看板指标", module = "DASHBOARD")
    @RequirePermission({"admin:dashboard:rebuild"})
    @PostMapping("/rebuild")
    public R<Integer> rebuild(
            @RequestParam(required = false, name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(dashboardService.rebuild(date));
    }
}
