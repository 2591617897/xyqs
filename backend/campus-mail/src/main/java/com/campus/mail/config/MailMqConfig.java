package com.campus.mail.config;

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
 * 队列拓扑。
 *
 * <p><b>注意</b>：campus-admin 侧也有一份完全相同的声明（它要保证投递前 exchange 已经存在）。
 * 两边的 durable / DLX 参数必须逐字一致 —— RabbitMQ 对同名队列的参数不一致会直接抛
 * PRECONDITION_FAILED，服务起不来。改参数请两边一起改。
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

    /**
     * 消息用 JSON，不用 Java 原生序列化。
     *
     * <p>生产方与消费方是两个独立部署的服务（还可能不同版本），
     * JDK 序列化把类名、serialVersionUID 全绑死，改个字段就反序列化失败；
     * JSON 至少能在字段增减时优雅降级。生产端也必须配同样的转换器。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
