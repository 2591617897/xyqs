package com.campus.user.client;

import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品计数内部接口客户端（campus-user → campus-goods）。
 * /internal/** 不经过网关，仅服务间调用；失败由调用方降级处理。
 */
@FeignClient(name = "campus-goods", contextId = "goodsCountClient", path = "/internal/goods")
public interface GoodsCountClient {

    /** 按作者计商品数；status 可选，不传 = 全部（已物理删除的不计） */
    @GetMapping("/count")
    R<Long> count(@RequestParam("userId") Long userId,
                  @RequestParam(value = "status", required = false) Integer status);

    /** 该作者商品收藏数 */
    @GetMapping("/favorites/count")
    R<Long> favoritesCount(@RequestParam("userId") Long userId);

    /** 该作者订单数：role=buyer/seller，status 可选（不传 = 全部） */
    @GetMapping("/orders/count")
    R<Long> ordersCount(@RequestParam("userId") Long userId,
                        @RequestParam("role") String role,
                        @RequestParam(value = "status", required = false) Integer status);
}
