package com.campus.admin.client;

import com.campus.admin.dto.AdminActionReq;
import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 后台 → campus-message 的处置通道：删除违规消息。
 *
 * <p>复用消息服务既有的软删语义（deleted=1），与用户自己「撤回」走同一列，
 * 不做物理删除——聊天记录是纠纷证据，删了就再也找不回来。
 */
@FeignClient(name = "campus-message", contextId = "adminMessageClient", path = "/internal/admin/messages")
public interface MessageAdminClient {

    @PostMapping("/{id}/delete")
    R<Void> delete(@PathVariable("id") Long id, @RequestBody AdminActionReq req);
}
