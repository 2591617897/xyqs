package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.AdminUserDTO;
import com.campus.admin.dto.AdminUserReq;
import com.campus.admin.dto.ResetPasswordReq;
import com.campus.admin.service.AdminUserService;
import com.campus.common.api.R;
import jakarta.validation.Valid;
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
 * 后台管理员账号维护。
 */
@RestController
@RequestMapping("/admins")
@RequirePermission({"admin:admin:list"})
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public R<R.Page<AdminUserDTO>> page(
            @RequestParam(required = false, name = "keyword") String keyword,
            @RequestParam(required = false, name = "roleId") Long roleId,
            @RequestParam(required = false, name = "status") Integer status,
            @RequestParam(defaultValue = "1", name = "page") long page,
            @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(adminUserService.page(keyword, roleId, status, page, size));
    }

    @RequirePermission({"admin:admin:detail"})
    @GetMapping("/{id}")
    public R<AdminUserDTO> detail(@PathVariable("id") Long id) {
        return R.ok(adminUserService.detail(id));
    }

    @AuditLog(value = "新建管理员", module = "SYSTEM")
    @RequirePermission({"admin:admin:create"})
    @PostMapping
    public R<Long> create(@RequestBody @Valid AdminUserReq req) {
        return R.ok(adminUserService.create(req));
    }

    @AuditLog(value = "修改管理员", module = "SYSTEM")
    @RequirePermission({"admin:admin:update"})
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody @Valid AdminUserReq req) {
        adminUserService.update(id, req);
        return R.ok();
    }

    @AuditLog(value = "删除管理员", module = "SYSTEM")
    @RequirePermission({"admin:admin:delete"})
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        adminUserService.delete(id);
        return R.ok();
    }

    @AuditLog(value = "启停管理员", module = "SYSTEM")
    @RequirePermission({"admin:admin:status"})
    @PostMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable("id") Long id,
                                @RequestParam(name = "status") Integer status) {
        adminUserService.changeStatus(id, status);
        return R.ok();
    }

    @AuditLog(value = "重置管理员密码", module = "SYSTEM", saveParams = false)
    @RequirePermission({"admin:admin:reset-pwd"})
    @PostMapping("/{id}/reset-pwd")
    public R<Void> resetPassword(@PathVariable("id") Long id,
                                 @RequestBody @Valid ResetPasswordReq req) {
        adminUserService.resetPassword(id, req.getNewPassword());
        return R.ok();
    }
}
