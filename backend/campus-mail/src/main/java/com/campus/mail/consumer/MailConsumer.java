package com.campus.mail.consumer;

import com.campus.common.mail.MailMessage;
import com.campus.common.mail.MailQueues;
import com.campus.mail.service.MailSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 邮件消息消费者。
 *
 * <p>这里刻意只做「转交」，异常一路冒泡：重试、死信由 AMQP 的监听容器按
 * {@code spring.rabbitmq.listener.simple.retry} 的配置处理。
 * 一旦在这里 catch 掉异常，MQ 会认为已消费，邮件就静默丢了。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailConsumer {

    private final MailSendService mailSendService;

    @RabbitListener(queues = MailQueues.QUEUE_SEND)
    public void onMessage(MailMessage msg) {
        log.info("[mail] 收到投递 requestId={} template={} biz={}",
                msg.getRequestId(), msg.getTemplate(), msg.getBizType());
        mailSendService.send(msg);
    }
}
