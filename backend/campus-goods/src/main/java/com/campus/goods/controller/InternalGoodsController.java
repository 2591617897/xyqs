package com.campus.goods.controller;

import com.campus.common.api.R;
import com.campus.goods.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
