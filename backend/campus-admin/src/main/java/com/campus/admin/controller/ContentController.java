package com.campus.admin.controller;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.CommentRow;
import com.campus.admin.dto.ContentDeleteReq;
import com.campus.admin.dto.DemandRow;
import com.campus.admin.dto.DisposeReq;
import com.campus.admin.dto.GoodsRow;
import com.campus.admin.dto.PostRow;
import com.campus.admin.dto.WallRow;
import com.campus.admin.entity.ContentAudit;
import com.campus.admin.service.ContentService;
import com.campus.common.api.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容管理：帖子 / 表白墙 / 二手商品 / 评论 / 处置流水。
 *
 * <p>权限点一个方法一个，不做类级兜底——内容管理里「能看」和「能处置」
 * 必须能分开授权（审核员通常只有查看权，处置权单独给）。
 */
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    // ---------------- 帖子 ----------------

    @RequirePermission({"admin:content:post:list"})
    @GetMapping("/posts")
    public R<R.Page<PostRow>> pagePosts(@RequestParam(required = false, name = "keyword") String keyword,
                                        @RequestParam(required = false, name = "status") Integer status,
                                        @RequestParam(required = false, name = "authorId") Long authorId,
                                        @RequestParam(defaultValue = "1", name = "page") long page,
                                        @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(contentService.pagePosts(keyword, status, authorId, page, size));
    }

    @RequirePermission({"admin:content:post:detail"})
    @GetMapping("/posts/{id}")
    public R<PostRow> postDetail(@PathVariable("id") Long id) {
        return R.ok(contentService.postDetail(id));
    }

    @AuditLog(value = "隐藏/恢复帖子", module = "CONTENT")
    @RequirePermission({"admin:content:post:hide"})
    @PostMapping("/posts/{id}/status")
    public R<Void> setPostStatus(@PathVariable("id") Long id, @RequestBody @Valid DisposeReq req) {
        contentService.setPostStatus(id, req.getStatus(), req.getReasonType(), req.getReason());
        return R.ok();
    }

    // ---------------- 表白墙 ----------------

    @RequirePermission({"admin:content:wall:list"})
    @GetMapping("/wall")
    public R<R.Page<WallRow>> pageWall(@RequestParam(required = false, name = "keyword") String keyword,
                                       @RequestParam(required = false, name = "status") Integer status,
                                       @RequestParam(required = false, name = "category") String category,
                                       @RequestParam(required = false, name = "authorId") Long authorId,
                                       @RequestParam(defaultValue = "1", name = "page") long page,
                                       @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(contentService.pageWall(keyword, status, category, authorId, page, size));
    }

    @RequirePermission({"admin:content:wall:detail"})
    @GetMapping("/wall/{id}")
    public R<WallRow> wallDetail(@PathVariable("id") Long id) {
        return R.ok(contentService.wallDetail(id));
    }

    @AuditLog(value = "隐藏/恢复表白墙", module = "CONTENT")
    @RequirePermission({"admin:content:wall:hide"})
    @PostMapping("/wall/{id}/status")
    public R<Void> setWallStatus(@PathVariable("id") Long id, @RequestBody @Valid DisposeReq req) {
        contentService.setWallStatus(id, req.getStatus(), req.getReasonType(), req.getReason());
        return R.ok();
    }

    // ---------------- 二手商品 ----------------

    @RequirePermission({"admin:content:goods:list"})
    @GetMapping("/goods")
    public R<R.Page<GoodsRow>> pageGoods(@RequestParam(required = false, name = "keyword") String keyword,
                                         @RequestParam(required = false, name = "status") Integer status,
                                         @RequestParam(required = false, name = "sellerId") Long sellerId,
                                         @RequestParam(defaultValue = "1", name = "page") long page,
                                         @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(contentService.pageGoods(keyword, status, sellerId, page, size));
    }

    @RequirePermission({"admin:content:goods:detail"})
    @GetMapping("/goods/{id}")
    public R<GoodsRow> goodsDetail(@PathVariable("id") Long id) {
        return R.ok(contentService.goodsDetail(id));
    }

    @AuditLog(value = "商品强制下架", module = "CONTENT")
    @RequirePermission({"admin:content:goods:off-shelf"})
    @PostMapping("/goods/{id}/status")
    public R<Void> setGoodsStatus(@PathVariable("id") Long id, @RequestBody @Valid DisposeReq req) {
        contentService.setGoodsStatus(id, req.getStatus(), req.getReasonType(), req.getReason());
        return R.ok();
    }

    // ---------------- 需求广场（求物 / 求帮忙） ----------------

    /** 分类字典先于列表加载，前端用它把 categoryId 渲染成中文 */
    @RequirePermission({"admin:content:demand:list"})
    @GetMapping("/demand-categories")
    public R<List<Map<String, Object>>> demandCategories() {
        return R.ok(contentService.demandCategories());
    }

    @RequirePermission({"admin:content:demand:list"})
    @GetMapping("/demands")
    public R<R.Page<DemandRow>> pageDemands(@RequestParam(required = false, name = "keyword") String keyword,
                                            @RequestParam(required = false, name = "type") Integer type,
                                            @RequestParam(required = false, name = "status") Integer status,
                                            @RequestParam(required = false, name = "userId") Long userId,
                                            @RequestParam(defaultValue = "1", name = "page") long page,
                                            @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(contentService.pageDemands(keyword, type, status, userId, page, size));
    }

    @RequirePermission({"admin:content:demand:detail"})
    @GetMapping("/demands/{id}")
    public R<DemandRow> demandDetail(@PathVariable("id") Long id) {
        return R.ok(contentService.demandDetail(id));
    }

    @AuditLog(value = "需求强制下架", module = "CONTENT")
    @RequirePermission({"admin:content:demand:off-shelf"})
    @PostMapping("/demands/{id}/status")
    public R<Void> setDemandStatus(@PathVariable("id") Long id, @RequestBody @Valid DisposeReq req) {
        contentService.setDemandStatus(id, req.getStatus(), req.getReasonType(), req.getReason());
        return R.ok();
    }

    @AuditLog(value = "删除需求", module = "CONTENT")
    @RequirePermission({"admin:content:demand:delete"})
    @DeleteMapping("/demands/{id}")
    public R<Void> deleteDemand(@PathVariable("id") Long id, @RequestBody @Valid ContentDeleteReq req) {
        contentService.deleteDemand(id, req.getReasonType(), req.getReason());
        return R.ok();
    }

    // ---------------- 评论 ----------------

    @RequirePermission({"admin:content:comment:list"})
    @GetMapping("/comments")
    public R<R.Page<CommentRow>> pageComments(@RequestParam(required = false, name = "keyword") String keyword,
                                              @RequestParam(required = false, name = "targetType") String targetType,
                                              @RequestParam(required = false, name = "userId") Long userId,
                                              @RequestParam(defaultValue = "1", name = "page") long page,
                                              @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(contentService.pageComments(keyword, targetType, userId, page, size));
    }

    /**
     * 删除评论。targetType 与 targetId 必须由前端带上：
     * 评论 id 本身不含类型信息，不知道类型就没法路由到归属服务。
     */
    @AuditLog(value = "删除评论", module = "CONTENT")
    @RequirePermission({"admin:content:comment:delete"})
    @PostMapping("/comments/{id}/delete")
    public R<Void> deleteComment(@PathVariable("id") Long id,
                                 @RequestParam(name = "targetType") String targetType,
                                 @RequestParam(required = false, name = "targetId") Long targetId,
                                 @RequestBody(required = false) DisposeReq req) {
        contentService.deleteComment(targetType, id, targetId,
                req == null ? null : req.getReasonType(),
                req == null ? null : req.getReason());
        return R.ok();
    }

    // ---------------- 处置流水 ----------------

    @RequirePermission({"admin:content:audit:list"})
    @GetMapping("/audits")
    public R<R.Page<ContentAudit>> pageAudits(@RequestParam(required = false, name = "targetType") String targetType,
                                              @RequestParam(required = false, name = "action") String action,
                                              @RequestParam(required = false, name = "adminId") Long adminId,
                                              @RequestParam(defaultValue = "1", name = "page") long page,
                                              @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(contentService.pageAudits(targetType, action, adminId, page, size));
    }
}
