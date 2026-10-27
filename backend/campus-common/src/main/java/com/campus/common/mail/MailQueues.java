package com.campus.common.mail;

/**
 * 邮件队列 / 交换机名。
 *
 * <p><b>为什么放在 common</b>：生产者（各业务服务）和消费者（campus-mail）如果各写一份字符串，
 * 只要有一边改名或拼错，消息就会静默落到不存在的路由上——不报错、也不进死信，看起来「发出去了」。
 * 队列名、routing key 一律从这里取。
 *
 * <p>拓扑：
 * <pre>
 *   campus.mail (direct, durable)
 *     └─ routing: mail.send ──▶ campus.mail.send (durable)
 *                                  └─ 消费失败 3 次 ──▶ campus.mail.dlx ──▶ campus.mail.dlq
 * </pre>
 */
public final class MailQueues {

    private MailQueues() {
    }

    /** 业务交换机 */
    public static final String EXCHANGE = "campus.mail";

    /** 投递路由键 */
    public static final String ROUTING_SEND = "mail.send";

    /** 待发送队列 */
    public static final String QUEUE_SEND = "campus.mail.send";

    /** 死信交换机 / 路由键 / 队列：重试耗尽后落这里，人工排障用 */
    public static final String DLX = "campus.mail.dlx";
    public static final String ROUTING_DEAD = "mail.dead";
    public static final String QUEUE_DEAD = "campus.mail.dlq";
}
