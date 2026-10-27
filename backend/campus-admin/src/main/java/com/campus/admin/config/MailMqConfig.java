package com.campus.admin.config;

import com.campus.common.mail.MailQueues;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 邮件队列拓扑（生产端声明）。
 *
 * <p><b>为什么生产端也要声明</b>：如果只在 campus-mail 里声明，而本服务先于它启动，
 * 第一条消息会发到还不存在的 exchange 上 —— AMQP 不会报错，消息直接消失。
 *
 * <p>参数必须与 campus-mail 的 MailMqConfig <b>逐字一致</b>（durable、DLX、routing key）。
 * RabbitMQ 对同名队列参数不一致会抛 PRECONDITION_FAILED，服务直接起不来。改一边就要改另一边。
 */
@Configuration
public class MailMqConfig {

    @Bean
    public DirectExchange mailExchange() {
        return new DirectExchange(MailQueues.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange mailDeadExchange() {
        return new DirectExchange(MailQueues.DLX, true, false);
    }

    @Bean
    public Queue mailSendQueue() {
        return QueueBuilder.durable(MailQueues.QUEUE_SEND)
                .deadLetterExchange(MailQueues.DLX)
                .deadLetterRoutingKey(MailQueues.ROUTING_DEAD)
                .build();
    }

    @Bean
    public Queue mailDeadQueue() {
        return QueueBuilder.durable(MailQueues.QUEUE_DEAD).build();
    }

    @Bean
    public Binding mailSendBinding() {
        return BindingBuilder.bind(mailSendQueue()).to(mailExchange()).with(MailQueues.ROUTING_SEND);
    }

    @Bean
    public Binding mailDeadBinding() {
        return BindingBuilder.bind(mailDeadQueue()).to(mailDeadExchange()).with(MailQueues.ROUTING_DEAD);
    }

    /** 与 campus-mail 一样用 JSON，两端必须一致，否则消费端反序列化失败 */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
