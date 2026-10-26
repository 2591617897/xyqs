package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.entity.AdminNotice;
import com.campus.admin.service.NoticeService;
import com.campus.common.api.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知公告。草稿 → 已发布 → 已下线。
 */
@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @RequirePermission({"admin:notice:list"})
    @GetMapping
    public R<R.Page<AdminNotice>> page(@RequestParam(required = false, name = "keyword") String keyword,
                                       @RequestParam(required = false, name = "status") Integer status,
                                       @RequestParam(required = false, name = "popupMode") Integer popupMode,
                                       @RequestParam(required = false, name = "targetType") Integer targetType,
                                       @RequestParam(defaultValue = "1", name = "page") long page,
                                       @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(noticeService.pageNotices(keyword, status, popupMode, targetType, page, size));
    }

    @RequirePermission({"admin:notice:list"})
    @GetMapping("/{id}")
    public R<AdminNotice> detail(@PathVariable("id") Long id) {
        return R.ok(noticeService.detail(id));
    }

    @AuditLog(value = "新建公告", module = "NOTICE")
    @RequirePermission({"admin:notice:create"})
    @PostMapping
    public R<Long> create(@RequestBody AdminNotice notice) {
        return R.ok(noticeService.create(notice));
    }

    @AuditLog(value = "修改公告", module = "NOTICE")
    @RequirePermission({"admin:notice:update"})
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody AdminNotice req) {
        noticeService.update(id, req);
        return R.ok();
    }

    @AuditLog(value = "删除公告", module = "NOTICE")
    @RequirePermission({"admin:notice:delete"})
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        noticeService.delete(id);
        return R.ok();
    }

    @AuditLog(value = "发布公告", module = "NOTICE")
    @RequirePermission({"admin:notice:publish"})
    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable("id") Long id) {
        noticeService.publish(id);
        return R.ok();
    }

    @AuditLog(value = "下线公告", module = "NOTICE")
    @RequirePermission({"admin:notice:publish"})
    @PostMapping("/{id}/offline")
    public R<Void> offline(@PathVariable("id") Long id) {
        noticeService.offline(id);
        return R.ok();
    }
}
