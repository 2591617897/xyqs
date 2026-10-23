package com.campus.post.controller;

import com.campus.common.api.R;
import com.campus.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子服务内部接口（不经过网关，仅服务间 Feign 调用）。
 * 当前承载个人主页的「帖子数」聚合，调用方 campus-user。
 */
@RestController
@RequestMapping("/internal/posts")
@RequiredArgsConstructor
public class InternalPostController {

    private final PostService postService;

    /** 该作者 status=1 帖子数 */
    @GetMapping("/count")
    public R<Long> count(@RequestParam("userId") Long userId) {
        return R.ok(postService.countByUser(userId));
    }
}
