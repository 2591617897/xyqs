package com.campus.message.controller;

import com.campus.common.api.R;
import com.campus.message.dto.AutoJoinReq;
import com.campus.message.service.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Feign 内部接口（不经过网关）：campus-user 在「绑定学校」时调这里自动入群。
 *
 * <p>三条绑定路径（{@code PUT /user/me}、{@code POST /user/verify}、
 * {@code POST /user/verify/wx-student}）都必须调用，漏一条就是
 * 「用户认证了但群里没他」——而且不报错，用户只会觉得群没人。
 */
@Slf4j
@RestController
@RequestMapping("/internal/groups")
@RequiredArgsConstructor
public class InternalGroupController {

    private final GroupService groupService;

    /**
     * 自动入群（学校没有群则自动建群）。
     *
     * <p>调用方必须 fail-soft：入群失败不能影响「绑定学校」主流程。
     * 这里也允许幂等重复调用（重复调用不会重复加成员数）。
     */
    @PostMapping("/auto-join")
    public R<Map<String, Object>> autoJoin(@RequestBody AutoJoinReq req) {
        if (req.getUserId() == null || req.getSchoolId() == null) {
            return R.ok(Map.of("groupId", ""));
        }
        Long groupId = groupService.autoJoin(req.getUserId(), req.getSchoolId());
        return R.ok(Map.of("groupId", String.valueOf(groupId)));
    }
}
