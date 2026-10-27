package com.campus.user.client;

import com.campus.common.api.R;
import com.campus.user.dto.AutoJoinReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 校友群自动入群（campus-user → campus-message）。
 *
 * <p>调用点有三个，缺一不可：{@code PUT /user/me}（选校）、
 * {@code POST /user/verify}（学生证认证）、{@code POST /user/verify/wx-student}（微信学生认证）。
 * 漏掉任何一个，用户就会「认证成功但群里没他」，而且不报错。
 *
 * <p>调用方必须 fail-soft：入群失败不能影响「绑定学校」主流程；
 * 进群页还有一道 lazy join 自愈（见 {@code GroupService#detail}）。
 */
@FeignClient(name = "campus-message", contextId = "groupJoinClient", path = "/internal/groups")
public interface GroupJoinClient {

    @PostMapping("/auto-join")
    R<Map<String, Object>> autoJoin(@RequestBody AutoJoinReq req);
}
