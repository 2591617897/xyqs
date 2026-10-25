package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.AdminMenuReq;
import com.campus.admin.dto.MenuNode;
import com.campus.admin.entity.AdminMenu;
import com.campus.admin.service.MenuService;
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

/**
 * 菜单与权限点维护。
 *
 * <p>改这里要格外小心：component 字段与前端 admin-web/src/views 下的 .vue 一一对应，
 * 改了后端不改前端，动态路由会加载失败落到 404 页。
 */
@RestController
@RequestMapping("/menus")
@RequirePermission({"admin:menu:list"})
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    public R<List<MenuNode>> tree(@RequestParam(required = false, name = "type") Integer type) {
        return R.ok(menuService.tree(type));
    }

    @GetMapping("/{id}")
    public R<AdminMenu> detail(@PathVariable("id") Long id) {
        return R.ok(menuService.detail(id));
    }

    @AuditLog(value = "新建菜单", module = "SYSTEM")
    @RequirePermission({"admin:menu:create"})
    @PostMapping
    public R<Long> create(@RequestBody @Valid AdminMenuReq req) {
        return R.ok(menuService.create(req));
    }

    @AuditLog(value = "修改菜单", module = "SYSTEM")
    @RequirePermission({"admin:menu:update"})
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody @Valid AdminMenuReq req) {
        menuService.update(id, req);
        return R.ok();
    }

    @AuditLog(value = "删除菜单", module = "SYSTEM")
    @RequirePermission({"admin:menu:delete"})
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        menuService.delete(id);
        return R.ok();
    }
}
