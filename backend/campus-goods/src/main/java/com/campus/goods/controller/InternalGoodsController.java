package com.campus.goods.controller;

import com.campus.common.api.R;
import com.campus.goods.dto.PromoteApplyReq;
import com.campus.goods.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品服务内部接口（不经过网关，仅服务间 Feign 调用）。
 * 当前承载个人主页的「闲置在售 / 累计发布」聚合，调用方 campus-user。
 */
@RestController
@RequestMapping("/internal/goods")
@RequiredArgsConstructor
public class InternalGoodsController {

    private final GoodsService goodsService;

    /** 按作者计商品数；status 可选，不传 = 全部（已物理删除的不计） */
    @GetMapping("/count")
    public R<Long> count(@RequestParam("userId") Long userId,
                         @RequestParam(required = false, name = "status") Integer status) {
        return R.ok(goodsService.countByUser(userId, status));
    }

    /** 该作者商品收藏数 */
    @GetMapping("/favorites/count")
    public R<Long> favoritesCount(@RequestParam("userId") Long userId) {
        return R.ok(goodsService.countFavorites(userId));
    }

    /**
     * 商品作者（campus-promote 归属校验用）。
     * 不存在返回 null data，由调用方决定是 404 还是拒绝。
     */
    @GetMapping("/owner")
    public R<Long> owner(@RequestParam("id") Long id) {
        return R.ok(goodsService.ownerOf(id));
    }

    /**
     * 回写推广分（campus-promote 结算 / 衰减重算用）。
     * 幂等：同一份数据重复写结果一致。
     */
    @PostMapping("/promote/apply")
    public R<Void> applyPromote(@RequestBody PromoteApplyReq req) {
        goodsService.applyPromote(req.getTargetId(), req.getScore(), req.getExpireAt());
        return R.ok();
    }

    /** 该作者订单数：role=buyer/seller，status 可选（不传 = 全部） */
    @GetMapping("/orders/count")
    public R<Long> ordersCount(@RequestParam("userId") Long userId,
                               @RequestParam("role") String role,
                               @RequestParam(required = false, name = "status") Integer status) {
        return R.ok(goodsService.countOrders(userId, role, status));
    }
}
