package com.campus.goods.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.goods.dto.GoodsOrderReq;
import com.campus.goods.dto.GoodsReq;
import com.campus.goods.entity.Goods;
import com.campus.goods.entity.GoodsComment;
import com.campus.goods.entity.GoodsOrder;
import com.campus.goods.service.GoodsService;
import com.campus.goods.service.HotWordsService;
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

@RestController
@RequestMapping("/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    private final HotWordsService hotWordsService;

    /**
     * 商品列表（未登录也可浏览）。
     * userId：个人主页按作者过滤，返回该作者全部状态商品（0下架/1在售/2已售出），
     * 不加 status=1 与学校范围过滤。
     * scope=school（默认）：本校范围；scope=city：同城高校混排（city 缺省取用户学校所在市）。
     */
    @GetMapping
    public R<R.Page<Goods>> page(@RequestParam(required = false, name = "keyword") String keyword,
                                 @RequestParam(required = false, name = "categoryId") Integer categoryId,
                                 @RequestParam(required = false, defaultValue = "latest", name = "sort") String sort,
                                 @RequestParam(defaultValue = "1", name = "page") long page,
                                 @RequestParam(defaultValue = "10", name = "size") long size,
                                 @RequestParam(required = false, name = "schoolId") Long schoolId,
                                 @RequestParam(required = false, name = "scope") String scope,
                                 @RequestParam(required = false, name = "city") String city,
                                 @RequestParam(required = false, name = "userId") Long userId) {
        // 个人主页：按作者过滤，跳过学校/城市范围逻辑
        if (userId != null) {
            return R.ok(goodsService.page(keyword, categoryId, sort, page, size, null, null, userId));
        }
        Long schoolScope = schoolId != null ? schoolId : UserContext.schoolId();
        if ("city".equalsIgnoreCase(scope)) {
            String targetCity = city != null && !city.isBlank() ? city : goodsService.cityOfSchool(schoolScope);
            // schoolScope 必须透传：本市流里「本校内容无条件可见」要靠它判定，
            // 传 null 会让用户连自己学校发布的仅本校闲置都看不到。
            return R.ok(goodsService.page(keyword, categoryId, sort, page, size, schoolScope, targetCity, null));
        }
        return R.ok(goodsService.page(keyword, categoryId, sort, page, size, schoolScope, null, null));
    }

    @GetMapping("/categories")
    public R<List<GoodsService.CategoryVO>> categories() {
        return R.ok(goodsService.categories());
    }

    /**
     * 搜索热词「大家都在搜」。
     *
     * <p>数据来自近 N 天真实的搜索日志聚合（同人同日同词只算一次，刷不上去），
     * 只统计「搜出过结果」的关键词；日志还没攒够时自动用默认词补齐，
     * 所以这个接口<b>永远返回一屏可点的词</b>，前端不必再兜空态。
     *
     * <p>游客也能访问：路径落在 /api/goods 前缀下，网关对该前缀只放行 GET。
     */
    @GetMapping("/hot-words")
    public R<List<String>> hotWords(@RequestParam(required = false, name = "days") Integer days,
                                    @RequestParam(required = false, name = "limit") Integer limit) {
        return R.ok(hotWordsService.top(days, limit));
    }

    /** 我发布的商品 */
    @GetMapping("/mine")
    public R<List<Goods>> mine(@RequestParam(required = false, name = "status") Integer status) {
        return R.ok(goodsService.mine(UserContext.requireUserId(), status));
    }

    /** 我的收藏 */
    @GetMapping("/favorites")
    public R<List<Goods>> favorites() {
        return R.ok(goodsService.myFavorites(UserContext.requireUserId()));
    }

    /** 我的购买 / 售卖意向 */
    @GetMapping("/orders/mine")
    public R<List<GoodsOrder>> orders(@RequestParam(defaultValue = "buyer", name = "role") String role) {
        return R.ok(goodsService.myOrders(UserContext.requireUserId(), role));
    }

    @GetMapping("/{id}")
    public R<Goods> detail(@PathVariable("id") Long id) {
        return R.ok(goodsService.detail(id, UserContext.userId()));
    }

    @PostMapping
    public R<Long> publish(@RequestBody @Valid GoodsReq req) {
        return R.ok(goodsService.publish(req));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody @Valid GoodsReq req) {
        goodsService.update(id, req);
        return R.ok();
    }

    /** 0 下架 / 1 重新上架 / 2 标记已售出 */
    @PostMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable("id") Long id, @RequestParam Integer status) {
        goodsService.changeStatus(id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        goodsService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/like")
    public R<Boolean> like(@PathVariable("id") Long id) {
        return R.ok(goodsService.toggleLike(id, UserContext.requireUserId()));
    }

    /** 提交购买意向（联系卖家） */
    @PostMapping("/{id}/order")
    public R<Long> order(@PathVariable("id") Long id, @RequestBody @Valid GoodsOrderReq req) {
        return R.ok(goodsService.createOrder(id, req));
    }

    /** 商品评论列表（含楼中楼回复） */
    @GetMapping("/{id}/comments")
    public R<List<GoodsComment>> comments(@PathVariable("id") Long id) {
        return R.ok(goodsService.comments(id, UserContext.userId()));
    }

    /** 发表评论 / 回复 */
    @PostMapping("/{id}/comments")
    public R<Long> comment(@PathVariable("id") Long id, @RequestBody @Valid CommentReq req) {
        return R.ok(goodsService.addComment(id, req.getContent(), req.getImages(), req.getParentId(), req.getMentionUserIds()));
    }

    /** 删除自己的评论 */
    @DeleteMapping("/{goodsId}/comments/{id}")
    public R<Void> deleteComment(@PathVariable("goodsId") Long goodsId, @PathVariable("id") Long id) {
        goodsService.deleteComment(goodsId, id);
        return R.ok();
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

        /** 评论中 @ 的用户 id 列表，后端强校验必须是评论者已关注的人 */
        private List<Long> mentionUserIds;
    }
}
