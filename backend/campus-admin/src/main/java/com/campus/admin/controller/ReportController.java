package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.ReportHandleReq;
import com.campus.admin.entity.ReportTicket;
import com.campus.admin.service.ReportService;
import com.campus.common.api.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 举报工单受理。工单由小程序侧创建，后台只在这里推进状态。
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * ⚠️ startTime / endTime 必须和前端 el-date-picker 的 value-format 完全对齐
     * （`YYYY-MM-DD HH:mm:ss`）。之前漏了这两个参数，Spring 把多余参数当空气丢掉，
     * 表现是「提交时间筛选点了没反应、也不报错」——比报错更难查。
     */
    @RequirePermission({"admin:report:list"})
    @GetMapping
    public R<R.Page<ReportTicket>> page(@RequestParam(required = false, name = "status") Integer status,
                                        @RequestParam(required = false, name = "targetType") String targetType,
                                        @RequestParam(required = false, name = "keyword") String keyword,
                                        @RequestParam(required = false, name = "startTime")
                                        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                        @RequestParam(required = false, name = "endTime")
                                        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                        @RequestParam(defaultValue = "1", name = "page") long page,
                                        @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(reportService.pageTickets(status, targetType, keyword, startTime, endTime, page, size));
    }

    @RequirePermission({"admin:report:detail"})
    @GetMapping("/{id}")
    public R<ReportTicket> detail(@PathVariable("id") Long id) {
        return R.ok(reportService.ticketDetail(id));
    }

    /**
     * 受理。会按 handleResult 联动一次处置（下架/禁言/封禁），
     * 已处理的工单重复点会被拒绝。
     */
    @AuditLog(value = "受理举报工单", module = "REPORT")
    @RequirePermission({"admin:report:handle"})
    @PostMapping("/{id}/handle")
    public R<Void> handle(@PathVariable("id") Long id, @RequestBody @Valid ReportHandleReq req) {
        reportService.handle(id, req);
        return R.ok();
    }
}
