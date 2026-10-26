package com.campus.wall.controller;

import com.campus.common.api.R;
import com.campus.wall.dto.PromoteApplyReq;
import com.campus.wall.service.WallService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表白墙服务内部接口（不经过网关，仅服务间 Feign 调用）。
 * 承载「我的」页的表白 / 树洞计数聚合（调用方 campus-user）与推广分回写（调用方 campus-promote）。
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

    /** 帖子作者（campus-promote 归属校验用），不存在返回 null data */
    @GetMapping("/owner")
    public R<Long> owner(@RequestParam("id") Long id) {
        return R.ok(wallService.ownerOf(id));
    }

    /** 回写推广分（campus-promote 结算 / 衰减重算用），幂等 */
    @PostMapping("/promote/apply")
    public R<Void> applyPromote(@RequestBody PromoteApplyReq req) {
        wallService.applyPromote(req.getTargetId(), req.getScore(), req.getExpireAt());
        return R.ok();
    }
}
