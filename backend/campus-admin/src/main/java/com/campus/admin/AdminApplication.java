package com.campus.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 后台管理服务启动类。
 *
 * <p>与其余 7 个业务模块保持完全一致的四件套写法：
 * 仅 {@code @MapperScan} 换成自己的包（后台全部自己建表，不复用 common 的 social.mapper）。
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.campus.common.client", "com.campus.admin.client"})
@MapperScan("com.campus.admin.mapper")
@SpringBootApplication(scanBasePackages = "com.campus")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
