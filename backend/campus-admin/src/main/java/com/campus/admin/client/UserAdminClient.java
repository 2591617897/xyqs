package com.campus.admin.client;

import com.campus.admin.dto.AdminActionReq;
import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 后台 → campus-user 的处置通道：封禁/解封、禁言投影、实名审核结论。
 *
 * <p>三个动作写的都是 t_user 的列（status / mute_until / verify_status），
 * 这些列的归属方是 campus-user，后台不在自己库里维护第二份。
 * mute / verify 的入参比 AdminActionReq 多，直接用 Map 传，
 * 避免为了三个字段再给 common 加一个共享类。
 */
@FeignClient(name = "campus-user", contextId = "adminUserClient", path = "/internal/admin/users")
public interface UserAdminClient {

    /** 封禁 / 解封：status 0封禁 1正常 */
    @PostMapping("/{id}/status")
    R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody AdminActionReq req);

    /** 禁言投影：muteUntil 为 null 表示解除 */
    @PostMapping("/{id}/mute")
    R<Void> mute(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    /** 实名审核结论：verifyStatus 2通过 3驳回 */
    @PostMapping("/{id}/verify")
    R<Void> verify(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);
}
