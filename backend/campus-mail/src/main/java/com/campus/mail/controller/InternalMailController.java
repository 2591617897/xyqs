package com.campus.mail.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.common.mail.MailMessage;
import com.campus.mail.dto.MailSendReq;
import com.campus.mail.entity.MailLog;
import com.campus.mail.service.MailPublisher;
import com.campus.mail.service.MailSendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：不经过网关（网关没有 /api/mail 路由），只给其它微服务调用。
 *
 * <p>三条路各有用途：
 * <ul>
 *   <li>{@code POST /send} —— 同步等 SMTP 结果。适合「发不出去就必须报错」的场景</li>
 *   <li>{@code POST /queue} —— 进 MQ 立即返回，谁不关心发信时延用这个</li>
 *   <li>{@code GET /logs/{requestId}} —— 回头查这封信到底发出去没有</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/internal/mail")
@RequiredArgsConstructor
public class InternalMailController {

    private final MailSendService mailSendService;
    private final MailPublisher mailPublisher;

    /**
     * 共享口令。留空表示不校验（本机/内网开发）；
     * 生产建议通过 MAIL_INTERNAL_TOKEN 配上，避免同网段任意进程借你们的域名发信。
     */
    @Value("${campus.mail.internal-token:}")
    private String internalToken;

    /** 同步发送：SMTP 失败会直接返回错误，便于调用方回滚业务 */
    @PostMapping("/send")
    public R<String> send(@RequestBody @Valid MailSendReq req,
                          @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternal(token);
        MailMessage msg = toMessage(req);
        mailSendService.send(msg);
        return R.ok(msg.getRequestId());
    }

    /** 异步发送：只保证投进了队列，真实结果用 /logs 查 */
    @PostMapping("/queue")
    public R<String> queue(@RequestBody @Valid MailSendReq req,
                           @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternal(token);
        return R.ok(mailPublisher.publish(toMessage(req)));
    }

    @GetMapping("/logs/{requestId}")
    public R<MailLog> log(@PathVariable String requestId,
                          @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        assertInternal(token);
        MailLog row = mailSendService.findByRequestId(requestId);
        if (row == null) {
            throw BizException.notFound("发信记录不存在");
        }
        return R.ok(row);
    }

    private MailMessage toMessage(MailSendReq req) {
        MailMessage msg = new MailMessage();
        msg.setRequestId(StringUtils.hasText(req.getRequestId()) ? req.getRequestId() : IdWorker.getIdStr());
        msg.setTo(req.getTo());
        msg.setTemplate(req.getTemplate());
        msg.setParams(req.getParams());
        msg.setBizType(req.getBizType());
        msg.setBizId(req.getBizId());
        return msg;
    }

    private void assertInternal(String token) {
        if (!StringUtils.hasText(internalToken)) {
            return;
        }
        if (!internalToken.equals(token)) {
            log.warn("[mail] 内部接口凭据无效");
            throw BizException.forbidden("内部调用凭据无效");
        }
    }
}
