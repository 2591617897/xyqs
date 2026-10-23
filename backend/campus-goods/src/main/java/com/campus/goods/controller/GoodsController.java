package com.campus.goods.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.goods.dto.GoodsOrderReq;
import com.campus.goods.dto.GoodsReq;
import com.campus.goods.entity.Goods;
import com.campus.goods.entity.GoodsOrder;
import com.campus.goods.service.GoodsService;
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

@RestController
@RequestMapping("/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    /** 商品列表（未登录也可浏览） */
    @GetMapping
    public R<R.Page<Goods>> page(@RequestParam(required = false, name = "keyword") String keyword,
                                 @RequestParam(required = false, name = "categoryId") Integer categoryId,
                                 @RequestParam(required = false, defaultValue = "latest", name = "sort") String sort,
                                 @RequestParam(defaultValue = "1", name = "page") long page,
                                 @RequestParam(defaultValue = "10", name = "size") long size,
                                 @RequestParam(required = false, name = "schoolId") Long schoolId) {
        Long scope = schoolId != null ? schoolId : UserContext.schoolId();
        return R.ok(goodsService.page(keyword, categoryId, sort, page, size, scope));
    }

    @GetMapping("/categories")
    public R<List<GoodsService.CategoryVO>> categories() {
        return R.ok(goodsService.categories());
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
}
