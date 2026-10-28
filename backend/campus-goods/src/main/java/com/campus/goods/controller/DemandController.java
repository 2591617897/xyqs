package com.campus.goods.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.goods.dto.DemandReplyReq;
import com.campus.goods.dto.DemandReq;
import com.campus.goods.entity.Demand;
import com.campus.goods.entity.DemandComment;
import com.campus.goods.entity.DemandReply;
import com.campus.goods.service.DemandMatchService;
import com.campus.goods.service.DemandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
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
 * 校园需求广场（求物 / 求帮忙）。
 *
 * <p>只读接口（GET）对游客放开，写接口一律要登录 + 学籍认证 ——
 * 与闲置模块同一口径，见网关的 PUBLIC_READ_PREFIX。
 */
@RestController
@RequestMapping("/demands")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;
    private final DemandMatchService demandMatchService;

    /**
     * 需求列表（未登录也可浏览）。
     *
     * <p>scope=school（默认）本校；scope=city 同城高校混排（city 缺省取用户学校所在市）。
     * type：1求物 2求帮忙，不传 = 全部。
     */
    @GetMapping
    public R<R.Page<Demand>> page(@RequestParam(required = false, name = "keyword") String keyword,
                                  @RequestParam(required = false, name = "type") Integer type,
                                  @RequestParam(required = false, name = "categoryId") Integer categoryId,
                                  @RequestParam(defaultValue = "1", name = "page") long page,
                                  @RequestParam(defaultValue = "10", name = "size") long size,
                                  @RequestParam(required = false, name = "schoolId") Long schoolId,
                                  @RequestParam(required = false, name = "scope") String scope,
                                  @RequestParam(required = false, name = "city") String city,
                                  @RequestParam(required = false, name = "userId") Long userId) {
        if (userId != null) {
            return R.ok(demandService.page(keyword, type, categoryId, page, size, null, null, userId));
        }
        Long schoolScope = schoolId != null ? schoolId : UserContext.schoolId();
        if ("city".equalsIgnoreCase(scope)) {
            String targetCity = city != null && !city.isBlank() ? city : demandService.cityOfSchool(schoolScope);
            // schoolScope 必须透传，否则本市流里连本校同学发的「仅本校」需求都刷不到
            return R.ok(demandService.page(keyword, type, categoryId, page, size, schoolScope, targetCity, null));
        }
        return R.ok(demandService.page(keyword, type, categoryId, page, size, schoolScope, null, null));
    }

    /** 需求分类；type 为空返回全部（含两类） */
    @GetMapping("/categories")
    public R<List<DemandService.DemandCategoryVO>> categories(
            @RequestParam(required = false, name = "type") Integer type) {
        return R.ok(demandService.categories(type));
    }

    /** 我发布的需求；status 不传 = 全部 */
    @GetMapping("/mine")
    public R<List<Demand>> mine(@RequestParam(required = false, name = "status") Integer status) {
        return R.ok(demandService.mine(UserContext.requireUserId(), status));
    }

    @GetMapping("/{id}")
    public R<Demand> detail(@PathVariable("id") Long id) {
        return R.ok(demandService.detail(id, UserContext.userId()));
    }

    @PostMapping
    public R<Long> publish(@RequestBody @Valid DemandReq req) {
        return R.ok(demandService.publish(req));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody @Valid DemandReq req) {
        demandService.update(id, req);
        return R.ok();
    }

    /**
     * 0 下架 / 1 重新开放 / 2 标记已完成（已找到）。
     *
     * <p>⚠️ status 必须走 @RequestBody：前端 `http.post` 发的是 JSON body，
     * 用 @RequestParam 取不到，Spring 会抛参数缺失异常 —— 全局兜底把它转成 500
     * 「服务开小差了」，看着像服务端崩了，实际是参数契约不匹配。</p>
     */
    @PostMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable("id") Long id, @RequestBody(required = false) StatusReq req) {
        demandService.changeStatus(id, req == null ? null : req.getStatus());
        return R.ok();
    }

    @Data
    public static class StatusReq {

        private Integer status;
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        demandService.delete(id);
        return R.ok();
    }

    /* ================= 回应 ================= */

    /** 需求详情 → 可能符合的在售闲置（互通推荐，最多 3 条） */
    @GetMapping("/{id}/matches")
    public R<List<DemandMatchService.MatchGoodsVO>> matches(@PathVariable("id") Long id) {
        return R.ok(demandMatchService.matchGoods(id, UserContext.schoolId()));
    }

    /** 我有货 / 我能帮：提交一条回应 */
    @PostMapping("/{id}/replies")
    public R<Long> reply(@PathVariable("id") Long id, @RequestBody @Valid DemandReplyReq req) {
        return R.ok(demandService.reply(id, req));
    }

    /** 我收到的回应（仅发布者可见，含联系方式明文） */
    @GetMapping("/{id}/replies")
    public R<List<DemandReply>> replies(@PathVariable("id") Long id) {
        return R.ok(demandService.replies(id, UserContext.requireUserId()));
    }

    /**
     * 我发出的回应。
     *
     * <p>路径刻意带 mine 段：网关的 PUBLIC_READ_PREFIX 放行 /api/demands 下的 GET，
     * 私域接口靠 PRIVATE_SEGMENTS 里的 "mine" 拦回去，不用在这里单独判空 token。
     */
    @GetMapping("/replies/mine")
    public R<List<DemandReply>> myReplies() {
        return R.ok(demandService.myReplies(UserContext.requireUserId()));
    }

    /* ================= 点赞与评论 ================= */

    /** 点赞 / 取消点赞一条需求（返回 true 表示最终是点赞态） */
    @PostMapping("/{id}/like")
    public R<Boolean> like(@PathVariable("id") Long id) {
        return R.ok(demandService.toggleLike(id, UserContext.requireUserId()));
    }

    /** 评论列表（含楼中楼回复） */
    @GetMapping("/{id}/comments")
    public R<List<DemandComment>> comments(@PathVariable("id") Long id) {
        return R.ok(demandService.comments(id, UserContext.userId()));
    }

    /** 发表评论 / 回复 */
    @PostMapping("/{id}/comments")
    public R<Long> comment(@PathVariable("id") Long id, @RequestBody @Valid CommentReq req) {
        return R.ok(demandService.addComment(id, req.getContent(), req.getImages(), req.getParentId()));
    }

    /** 删除评论：本人或需求发布者可删 */
    @DeleteMapping("/{demandId}/comments/{id}")
    public R<Void> deleteComment(@PathVariable("demandId") Long demandId,
                                 @PathVariable("id") Long id) {
        demandService.deleteComment(demandId, id);
        return R.ok();
    }

    /** 点赞 / 取消点赞一条评论 */
    @PostMapping("/comments/{id}/like")
    public R<Boolean> commentLike(@PathVariable("id") Long id) {
        return R.ok(demandService.toggleCommentLike(id, UserContext.requireUserId()));
    }

    @Data
    public static class CommentReq {

        @Size(max = 500, message = "评论不超过 500 字")
        private String content;

        /** 评论配图，最多 3 张 */
        @Size(max = 3, message = "评论最多 3 张图")
        private List<String> images;

        /** 回复的父评论 id，空 / 0 表示直接评论 */
        private Long parentId;
    }
}
