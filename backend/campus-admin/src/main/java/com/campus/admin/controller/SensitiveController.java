package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.entity.SensitiveWord;
import com.campus.admin.service.SensitiveService;
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

import java.util.List;

/**
 * 敏感词库。增删改会自动刷新内存缓存，另外提供显式的 reload 兜底。
 */
@RestController
@RequestMapping("/sensitive")
@RequiredArgsConstructor
public class SensitiveController {

    private final SensitiveService sensitiveService;

    @RequirePermission({"admin:sensitive:list"})
    @GetMapping
    public R<R.Page<SensitiveWord>> page(@RequestParam(required = false, name = "keyword") String keyword,
                                         @RequestParam(required = false, name = "level") Integer level,
                                         @RequestParam(required = false, name = "status") Integer status,
                                         @RequestParam(defaultValue = "1", name = "page") long page,
                                         @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(sensitiveService.pageWords(keyword, level, status, page, size));
    }

    @AuditLog(value = "新建敏感词", module = "SENSITIVE")
    @RequirePermission({"admin:sensitive:create"})
    @PostMapping
    public R<Long> create(@RequestBody SensitiveWord word) {
        return R.ok(sensitiveService.create(word));
    }

    @AuditLog(value = "修改敏感词", module = "SENSITIVE")
    @RequirePermission({"admin:sensitive:update"})
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody SensitiveWord req) {
        sensitiveService.update(id, req);
        return R.ok();
    }

    @AuditLog(value = "删除敏感词", module = "SENSITIVE")
    @RequirePermission({"admin:sensitive:delete"})
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        sensitiveService.delete(id);
        return R.ok();
    }

    @AuditLog(value = "刷新敏感词缓存", module = "SENSITIVE")
    @RequirePermission({"admin:sensitive:reload"})
    @PostMapping("/reload")
    public R<Integer> reload() {
        sensitiveService.reload();
        return R.ok(sensitiveService.getCache().size());
    }

    /**
     * 试检测：给运营验证词库效果，不写库、不产生任何副作用。
     */
    @RequirePermission({"admin:sensitive:list"})
    @GetMapping("/check")
    public R<List<SensitiveWord>> check(@RequestParam(name = "text") String text) {
        return R.ok(sensitiveService.check(text));
    }
}
