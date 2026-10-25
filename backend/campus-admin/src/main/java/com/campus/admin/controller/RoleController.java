package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.AdminRoleReq;
import com.campus.admin.entity.AdminRole;
import com.campus.admin.service.RoleService;
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

import java.util.List;
import java.util.Map;

/**
 * 角色管理与授权。
 */
@RestController
@RequestMapping("/roles")
@RequirePermission({"admin:role:list"})
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public R<R.Page<AdminRole>> page(
            @RequestParam(required = false, name = "keyword") String keyword,
            @RequestParam(required = false, name = "status") Integer status,
            @RequestParam(defaultValue = "1", name = "page") long page,
            @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(roleService.page(keyword, status, page, size));
    }

    @RequirePermission({"admin:role:detail"})
    @GetMapping("/{id}")
    public R<AdminRole> detail(@PathVariable("id") Long id) {
        return R.ok(roleService.detail(id));
    }

    @AuditLog(value = "新建角色", module = "SYSTEM")
    @RequirePermission({"admin:role:create"})
    @PostMapping
    public R<Long> create(@RequestBody @Valid AdminRoleReq req) {
        return R.ok(roleService.create(req));
    }

    @AuditLog(value = "修改角色", module = "SYSTEM")
    @RequirePermission({"admin:role:update"})
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody @Valid AdminRoleReq req) {
        roleService.update(id, req);
        return R.ok();
    }

    @AuditLog(value = "删除角色", module = "SYSTEM")
    @RequirePermission({"admin:role:delete"})
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @RequirePermission({"admin:role:grant"})
    @GetMapping("/{id}/menus")
    public R<List<Long>> menuIds(@PathVariable("id") Long id) {
        return R.ok(roleService.menuIdsOf(id));
    }

    /**
     * 角色授权。前端用 el-tree 勾选后整份提交，后端全量覆盖。
     */
    @AuditLog(value = "角色分配权限", module = "SYSTEM")
    @RequirePermission({"admin:role:grant"})
    @PutMapping("/{id}/menus")
    public R<Void> grantMenus(@PathVariable("id") Long id, @RequestBody Map<String, List<Long>> body) {
        roleService.grantMenus(id, body.get("menuIds"));
        return R.ok();
    }
}
