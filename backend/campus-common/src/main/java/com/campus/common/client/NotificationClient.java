package com.campus.common.client;

import com.campus.common.api.R;
import com.campus.common.client.dto.NotifyReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 通知中心 Feign 客户端，指向 campus-message 服务的 /internal/notifications 入口。
 * 各业务服务（post / wall / goods / user）注入此接口异步发通知。
 * 通知写入失败只 log.warn，不向上抛，避免阻塞主业务流程。
 */
@FeignClient(name = "campus-message", contextId = "campusMessageClient", path = "/internal/notifications")
public interface NotificationClient {

    @PostMapping("/send")
    R<Void> send(@RequestBody NotifyReq req);

    @PostMapping("/send-batch")
    R<Void> sendBatch(@RequestBody List<NotifyReq> reqs);
}
