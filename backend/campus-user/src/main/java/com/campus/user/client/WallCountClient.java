package com.campus.user.client;

import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 表白墙计数内部接口客户端（campus-user → campus-wall）。
 * /internal/** 不经过网关，仅服务间调用；失败由调用方降级处理。
 */
@FeignClient(name = "campus-wall", contextId = "wallCountClient", path = "/internal/wall/posts")
public interface WallCountClient {

    /** 该作者表白墙帖子数；category 可选（love 表白 / tree 树洞等），不传 = 全部 */
    @GetMapping("/count")
    R<Long> count(@RequestParam("userId") Long userId,
                  @RequestParam(value = "category", required = false) String category);
}