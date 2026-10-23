package com.campus.common.client;

import com.campus.common.client.dto.LoginOrCreateReq;
import com.campus.common.client.dto.UserBrief;
import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务内部接口客户端（公共模块，各业务服务共用）。
 * /internal/** 不经过网关暴露。
 */
@FeignClient(name = "campus-user", contextId = "campusUserClient", path = "/internal/users")
public interface UserClient {

    @PostMapping("/login-or-create")
    R<UserBrief> loginOrCreate(@RequestBody LoginOrCreateReq req);

    @GetMapping("/{id}")
    R<UserBrief> getBrief(@PathVariable("id") Long id);

    /** 批量摘要，避免列表场景 N+1 */
    @GetMapping("/briefs")
    R<List<UserBrief>> getBriefs(@RequestParam("ids") List<Long> ids);
}
