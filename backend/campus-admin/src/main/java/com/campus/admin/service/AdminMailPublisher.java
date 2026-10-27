package com.campus.admin.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.campus.common.mail.MailMessage;
import com.campus.common.mail.MailQueues;
import com.campus.common.mail.MailTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * 找回密码邮件的投递方。
 *
 * <p>这里<b>只投递、不发信</b>：SMTP 凭据与正文排版都在 campus-mail，
 * 本服务连 JavaMailSender 都不引。消息进了队列就返回，真实发信结果由
 * 邮件服务的 t_mail_log 记录（GET /internal/mail/logs/{requestId}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMailPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 投递验证码邮件。
     *
     * @return requestId，可用于事后查发信结果
     * @throws org.springframework.amqp.AmqpException broker 不可用（调用方需回滚已落库的验证码）
     */
    public String publishResetCode(String to, String username, String code,
                                   int expireMinutes, String ip, Long adminId) {
        MailMessage msg = new MailMessage();
        msg.setRequestId(IdWorker.getIdStr());
        msg.setTo(to);
        msg.setTemplate(MailTemplates.ADMIN_RESET_CODE);
        msg.setBizType(MailTemplates.ADMIN_RESET_CODE);
        msg.setBizId(adminId == null ? null : String.valueOf(adminId));
        msg.put("username", username)
                .put("code", code)
                .put("expireMinutes", String.valueOf(expireMinutes));
        if (ip != null) {
            msg.put("ip", ip);
        }
        rabbitTemplate.convertAndSend(MailQueues.EXCHANGE, MailQueues.ROUTING_SEND, msg);
        log.info("[mail] 找回密码验证码已投递 requestId={} admin={}", msg.getRequestId(), username);
        return msg.getRequestId();
    }
}
