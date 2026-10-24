package com.campus.message.controller;

import com.campus.common.api.R;
import com.campus.message.dto.NotifyReq;
import com.campus.message.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Feign 内部接口（不经过网关，仅内部调用）。
 * campus-post / campus-wall / campus-goods / campus-user 通过 NotificationClient 调这里发通知。
 * 所有写入异步，永不阻塞主业务流程。
 */
@Slf4j
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;

    /** 单条发送 */
    @PostMapping("/send")
    public R<Void> send(@RequestBody NotifyReq req) {
        notificationService.send(req);
        return R.ok();
    }

    /** 批量（一条评论 @ 多人时用） */
    @PostMapping("/send-batch")
    public R<Void> sendBatch(@RequestBody List<NotifyReq> reqs) {
        notificationService.sendBatch(reqs);
        return R.ok();
    }
}
