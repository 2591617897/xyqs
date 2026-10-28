package com.campus.post;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.campus.common.client")
@MapperScan({"com.campus.post.mapper", "com.campus.common.social.mapper"})
@SpringBootApplication(scanBasePackages = "com.campus")
// 广告曝光/点击靠定时任务聚合刷写（AdMetricsService#flush），没有它计数只进内存不落库
@EnableScheduling
public class PostApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostApplication.class, args);
    }
}
