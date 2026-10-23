package com.campus.wall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@MapperScan({"com.campus.wall.mapper", "com.campus.common.social.mapper"})
@SpringBootApplication(scanBasePackages = "com.campus")
public class WallApplication {

    public static void main(String[] args) {
        SpringApplication.run(WallApplication.class, args);
    }
}
