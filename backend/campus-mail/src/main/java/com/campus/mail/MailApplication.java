package com.campus.mail;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 邮件服务。
 *
 * <p>职责只有一件：把「谁、用什么模板、带什么参数」变成一封真正发出去的信，
 * 并把结果记进 t_mail_log。业务服务不再持有 SMTP 凭据，也不再各自拼邮件正文。
 */
@EnableDiscoveryClient
@MapperScan("com.campus.mail.mapper")
@SpringBootApplication(scanBasePackages = "com.campus")
public class MailApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailApplication.class, args);
    }
}
