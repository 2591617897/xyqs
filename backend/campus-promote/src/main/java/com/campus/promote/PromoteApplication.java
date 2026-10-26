package com.campus.promote;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableScheduling
@EnableFeignClients(basePackages = {"com.campus.common.client", "com.campus.promote.client"})
@MapperScan({"com.campus.promote.mapper"})
@SpringBootApplication(scanBasePackages = "com.campus")
public class PromoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromoteApplication.class, args);
    }
}
