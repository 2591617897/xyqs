package com.campus.common.client;

import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 关注关系 Feign 客户端，指向 campus-user 服务的 /internal/follows 入口。
 * 评论 @ 功能校验：只能 @ 评论者已关注的人。
 */
@FeignClient(name = "campus-user", contextId = "campusFollowClient", path = "/internal/follows")
public interface FollowClient {

    /**
     * 批量校验 @ 列表：返回 followerId 真正关注的那些 followeeId。
     * 前端传来的 mentionUserIds 可能含未关注的人（前端限制选择器但仍做强校验），
     * 这里过滤掉非法 id 后返回合法集合。
     */
    @GetMapping("/check")
    R<List<Long>> check(@RequestParam("followerId") Long followerId,
                        @RequestParam(name = "followeeIds") List<Long> followeeIds);
}
