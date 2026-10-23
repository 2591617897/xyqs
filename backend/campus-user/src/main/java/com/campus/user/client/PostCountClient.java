package com.campus.user.client;

import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 帖子计数内部接口客户端（campus-user → campus-post）。
 * /internal/** 不经过网关，仅服务间调用；失败由调用方降级处理。
 */
@FeignClient(name = "campus-post", contextId = "postCountClient", path = "/internal/posts")
public interface PostCountClient {

    /** 该作者 status=1 帖子数 */
    @GetMapping("/count")
    R<Long> count(@RequestParam("userId") Long userId);
}
