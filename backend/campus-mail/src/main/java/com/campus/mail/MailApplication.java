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
// 不能写 scanBasePackages = "com.campus"：那会把 common 的 SecurityGuard 一起扫进来，
// 它构造依赖 Feign 的 SecurityClient，而邮件服务既不需要调内容安全、也不该为它引入 Feign。
// 只挑邮件服务真正要用的三块公共配置：Jackson、全局异常、MyBatis-Plus。
@SpringBootApplication(scanBasePackages = {
        "com.campus.mail",
        "com.campus.common.config",
        "com.campus.common.exception",
        "com.campus.common.mybatis"})
public class MailApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailApplication.class, args);
    }
}
