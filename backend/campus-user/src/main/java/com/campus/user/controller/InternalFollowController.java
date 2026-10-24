package com.campus.user.controller;

import com.campus.common.api.R;
import com.campus.user.mapper.FollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注关系内部接口（不经过网关，仅服务间 Feign 调用）。
 * 核心：评论 @ 功能校验——只能 @ 当前登录用户已关注的人。
 */
@Slf4j
@RestController
@RequestMapping("/internal/follows")
@RequiredArgsConstructor
public class InternalFollowController {

    private final FollowMapper followMapper;

    /**
     * 校验 @ 列表：返回 followerId 真正关注的那些 followeeId。
     * 前端传来的 mentionUserIds 可能含未关注的人（前端限制选择器但仍做强校验），
     * 这里过滤掉非法 id 后返回合法集合。
     */
    @GetMapping("/check")
    public R<List<Long>> check(@RequestParam("followerId") Long followerId,
                               @RequestParam(name = "followeeIds") List<Long> followeeIds) {
        if (followerId == null || followeeIds == null || followeeIds.isEmpty()) {
            return R.ok(new ArrayList<>());
        }
        // 从 follower 真正关注的人里过滤
        List<com.campus.user.entity.Follow> follows = followMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.campus.user.entity.Follow>lambdaQuery()
                        .eq(com.campus.user.entity.Follow::getFollowerId, followerId)
                        .in(com.campus.user.entity.Follow::getFolloweeId, followeeIds)
        );
        Set<Long> valid = follows.stream().map(com.campus.user.entity.Follow::getFolloweeId).collect(Collectors.toSet());
        return R.ok(followeeIds.stream().filter(valid::contains).collect(Collectors.toList()));
    }
}
