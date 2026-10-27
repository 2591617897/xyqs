package com.campus.mail.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.campus.common.mail.MailMessage;
import com.campus.common.mail.MailQueues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 投递邮件消息到 MQ。
 *
 * <p>业务服务自己直接发布也可以（队列拓扑在各自配置里声明过），
 * 这个类主要给 {@code POST /internal/mail/queue} 用：不想引 AMQP 依赖的服务，
 * 直接 HTTP 打过来就行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailPublisher {

    private final RabbitTemplate rabbitTemplate;

    public String publish(MailMessage msg) {
        if (msg == null) {
            throw new IllegalArgumentException("邮件消息为空");
        }
        if (!StringUtils.hasText(msg.getRequestId())) {
            msg.setRequestId(IdWorker.getIdStr());
        }
        rabbitTemplate.convertAndSend(MailQueues.EXCHANGE, MailQueues.ROUTING_SEND, msg);
        log.info("[mail] 已投递 requestId={} template={} to={}",
                msg.getRequestId(), msg.getTemplate(), msg.getTo());
        return msg.getRequestId();
    }
}
