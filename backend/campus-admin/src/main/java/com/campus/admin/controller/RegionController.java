package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.RegionNode;
import com.campus.admin.entity.AdminRegion;
import com.campus.admin.entity.AdminRegionLeader;
import com.campus.admin.service.RegionService;
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
 * 组织架构与负责人。这棵树直接决定数据权限，删除前会校验子节点与负责人。
 */
@RestController
@RequestMapping("/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @RequirePermission({"admin:region:list"})
    @GetMapping("/tree")
    public R<List<RegionNode>> tree() {
        return R.ok(regionService.tree());
    }

    @AuditLog(value = "新建组织节点", module = "SYSTEM")
    @RequirePermission({"admin:region:create"})
    @PostMapping
    public R<Long> create(@RequestBody AdminRegion region) {
        return R.ok(regionService.create(region));
    }

    @AuditLog(value = "修改组织节点", module = "SYSTEM")
    @RequirePermission({"admin:region:update"})
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody AdminRegion req) {
        regionService.update(id, req);
        return R.ok();
    }

    @AuditLog(value = "删除组织节点", module = "SYSTEM")
    @RequirePermission({"admin:region:delete"})
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        regionService.delete(id);
        return R.ok();
    }

    @RequirePermission({"admin:leader:list"})
    @GetMapping("/leaders")
    public R<List<AdminRegionLeader>> leaders(@RequestParam(required = false, name = "regionId") Long regionId) {
        return R.ok(regionService.leaders(regionId));
    }

    /**
     * 指派负责人。成功后该管理员的数据范围会自动切成「仅辖区」，
     * 否则身份与权限不一致，等于给了个假权限。
     */
    @AuditLog(value = "指派负责人", module = "SYSTEM")
    @RequirePermission({"admin:leader:assign"})
    @PostMapping("/leaders/assign")
    public R<Long> assignLeader(@RequestParam(name = "regionId") Long regionId,
                                @RequestParam(name = "adminId") Long adminId,
                                @RequestParam(required = false, name = "leaderType") Integer leaderType) {
        return R.ok(regionService.assignLeader(regionId, adminId, leaderType));
    }

    @AuditLog(value = "撤销负责人", module = "SYSTEM")
    @RequirePermission({"admin:leader:revoke"})
    @PostMapping("/leaders/{id}/revoke")
    public R<Void> revokeLeader(@PathVariable("id") Long id) {
        regionService.revokeLeader(id);
        return R.ok();
    }
}
