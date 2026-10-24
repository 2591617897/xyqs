package com.campus.wall.controller;

import com.campus.common.api.R;
import com.campus.wall.service.WallService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表白墙服务内部接口（不经过网关，仅服务间 Feign 调用）。
 * 承载「我的」页的表白 / 树洞计数聚合，调用方 campus-user。
 */
@RestController
@RequestMapping("/internal/wall/posts")
@RequiredArgsConstructor
public class InternalWallController {

    private final WallService wallService;

    /** 该作者表白墙帖子数；category 可选（love 表白 / tree 树洞等），不传 = 全部 */
    @GetMapping("/count")
    public R<Long> count(@RequestParam("userId") Long userId,
                         @RequestParam(required = false, name = "category") String category) {
        return R.ok(wallService.countByUser(userId, category));
    }
}