package com.campus.admin.controller;

import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.entity.AdminLoginLog;
import com.campus.admin.entity.AdminOperationLog;
import com.campus.admin.service.LoginLogService;
import com.campus.admin.service.OperationLogService;
import com.campus.common.api.R;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 后台日志查询。
 */
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final OperationLogService operationLogService;
    private final LoginLogService loginLogService;

    @RequirePermission({"admin:log:operation:list"})
    @GetMapping("/operations")
    public R<R.Page<AdminOperationLog>> operations(
            @RequestParam(required = false, name = "adminId") Long adminId,
            @RequestParam(required = false, name = "username") String username,
            @RequestParam(required = false, name = "module") String module,
            @RequestParam(required = false, name = "result") Integer result,
            @RequestParam(required = false, name = "startTime")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false, name = "endTime")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1", name = "page") long page,
            @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(operationLogService.page(adminId, username, module, result, startTime, endTime, page, size));
    }

    @RequirePermission({"admin:log:operation:detail"})
    @GetMapping("/operations/{id}")
    public R<AdminOperationLog> operationDetail(@PathVariable("id") Long id) {
        return R.ok(operationLogService.detail(id));
    }

    @RequirePermission({"admin:log:login:list"})
    @GetMapping("/logins")
    public R<R.Page<AdminLoginLog>> logins(
            @RequestParam(required = false, name = "username") String username,
            @RequestParam(required = false, name = "status") Integer status,
            @RequestParam(required = false, name = "startTime")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false, name = "endTime")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1", name = "page") long page,
            @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(loginLogService.page(username, status, startTime, endTime, page, size));
    }
}
