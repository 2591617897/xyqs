package com.campus.goods;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} 是给搜索日志清理用的（{@code SearchLogCleanTask}）。
 * 不加这个注解 {@code @Scheduled} 不会生效，而且<b>不报错</b>——
 * 表现是「日志表一天天变大，但没有任何异常」，很难从现象倒推回来。
 */
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.campus.common.client")
@MapperScan({"com.campus.goods.mapper", "com.campus.common.social.mapper"})
@SpringBootApplication(scanBasePackages = "com.campus")
public class GoodsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodsApplication.class, args);
    }
}
