package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.AuditReq;
import com.campus.admin.entity.AdMaterial;
import com.campus.admin.entity.AdSlot;
import com.campus.admin.service.AdService;
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
 * 广告位与素材。审核与上下架是两道独立的闸，未过审不允许上架。
 */
@RestController
@RequestMapping("/ads")
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;

    // ---------------- 广告位 ----------------

    @RequirePermission({"admin:ad:slot:list"})
    @GetMapping("/slots")
    public R<R.Page<AdSlot>> pageSlots(@RequestParam(required = false, name = "keyword") String keyword,
                                       @RequestParam(required = false, name = "position") Integer position,
                                       @RequestParam(required = false, name = "scopeType") Integer scopeType,
                                       @RequestParam(required = false, name = "status") Integer status,
                                       @RequestParam(defaultValue = "1", name = "page") long page,
                                       @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(adService.pageSlots(keyword, position, scopeType, status, page, size));
    }

    @AuditLog(value = "新建广告位", module = "AD")
    @RequirePermission({"admin:ad:slot:create"})
    @PostMapping("/slots")
    public R<Long> createSlot(@RequestBody AdSlot slot) {
        return R.ok(adService.createSlot(slot));
    }

    @AuditLog(value = "修改广告位", module = "AD")
    @RequirePermission({"admin:ad:slot:update"})
    @PutMapping("/slots/{id}")
    public R<Void> updateSlot(@PathVariable("id") Long id, @RequestBody AdSlot req) {
        adService.updateSlot(id, req);
        return R.ok();
    }

    @AuditLog(value = "删除广告位", module = "AD")
    @RequirePermission({"admin:ad:slot:delete"})
    @DeleteMapping("/slots/{id}")
    public R<Void> deleteSlot(@PathVariable("id") Long id) {
        adService.deleteSlot(id);
        return R.ok();
    }

    @AuditLog(value = "审核广告位", module = "AD")
    @RequirePermission({"admin:ad:slot:update"})
    @PostMapping("/slots/{id}/audit")
    public R<Void> auditSlot(@PathVariable("id") Long id, @RequestBody AuditReq req) {
        if (req.getPass() == null) {
            throw new IllegalArgumentException("pass 不能为空");
        }
        adService.auditSlot(id, req.getPass(), req.getReason());
        return R.ok();
    }

    @AuditLog(value = "广告位启停", module = "AD")
    @RequirePermission({"admin:ad:slot:status"})
    @PostMapping("/slots/{id}/status")
    public R<Void> setSlotStatus(@PathVariable("id") Long id, @RequestParam(name = "status") Integer status) {
        adService.setSlotStatus(id, status);
        return R.ok();
    }

    // ---------------- 素材 ----------------

    @RequirePermission({"admin:ad:material:list"})
    @GetMapping("/materials")
    public R<R.Page<AdMaterial>> pageMaterials(@RequestParam(required = false, name = "slotId") Long slotId,
                                               @RequestParam(required = false, name = "keyword") String keyword,
                                               @RequestParam(required = false, name = "status") Integer status,
                                               @RequestParam(required = false, name = "auditStatus") Integer auditStatus,
                                               @RequestParam(defaultValue = "1", name = "page") long page,
                                               @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(adService.pageMaterials(slotId, keyword, status, auditStatus, page, size));
    }

    @AuditLog(value = "新建广告素材", module = "AD")
    @RequirePermission({"admin:ad:material:create"})
    @PostMapping("/materials")
    public R<Long> createMaterial(@RequestBody AdMaterial material) {
        return R.ok(adService.createMaterial(material));
    }

    @AuditLog(value = "修改广告素材", module = "AD")
    @RequirePermission({"admin:ad:material:update"})
    @PutMapping("/materials/{id}")
    public R<Void> updateMaterial(@PathVariable("id") Long id, @RequestBody AdMaterial req) {
        adService.updateMaterial(id, req);
        return R.ok();
    }

    @AuditLog(value = "删除广告素材", module = "AD")
    @RequirePermission({"admin:ad:material:delete"})
    @DeleteMapping("/materials/{id}")
    public R<Void> deleteMaterial(@PathVariable("id") Long id) {
        adService.deleteMaterial(id);
        return R.ok();
    }

    @AuditLog(value = "审核广告素材", module = "AD")
    @RequirePermission({"admin:ad:material:audit"})
    @PostMapping("/materials/{id}/audit")
    public R<Void> auditMaterial(@PathVariable("id") Long id, @RequestBody AuditReq req) {
        if (req.getPass() == null) {
            throw new IllegalArgumentException("pass 不能为空");
        }
        adService.auditMaterial(id, req.getPass(), req.getReason());
        return R.ok();
    }

    @AuditLog(value = "素材上下架", module = "AD")
    @RequirePermission({"admin:ad:material:online"})
    @PostMapping("/materials/{id}/status")
    public R<Void> setMaterialStatus(@PathVariable("id") Long id, @RequestParam(name = "status") Integer status) {
        adService.setMaterialStatus(id, status);
        return R.ok();
    }
}
