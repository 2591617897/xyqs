package com.campus.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 私信 / 通知 / 校友群服务。
 *
 * <p>⚠️ {@code @EnableScheduling} 不能删：群消息归档、WebSocket 心跳、
 * 频率闸清理、WS 票据清理全靠 {@code @Scheduled}。
 * <b>缺这个注解时 {@code @Scheduled} 不生效，而且不报错</b> ——
 * 表现是「归档任务永远不跑、死连接永远不清理」，很难联想到是少了个注解。
 */
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.campus.common.client")
@MapperScan("com.campus.message.mapper")
@SpringBootApplication(scanBasePackages = "com.campus")
public class MessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
    }
}
