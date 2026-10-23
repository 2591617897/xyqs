package com.campus.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 把上传目录映射成 /uploads/** 静态资源，供网关直接回源。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    @Value("${campus.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 注意：必须用 Path#toUri 生成 file:///C:/xxx 形式。
     * 手拼 "file:" + "C:/xxx" 会得到 file:C:/xxx（少一个斜杠），
     * Spring 的 PathResourceResolver 解析失败 → 映射静默失效，
     * 请求最终抛 NoResourceFoundException，被全局兜底 handler 变成 500 JSON，
     * 前端 &lt;image&gt; 拿到 JSON 就只显示灰块，而且因为 HTTP 200 连 binderror 都不触发。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path abs = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location = abs.toUri().toString();
        log.info("[upload] 静态资源映射 /uploads/** -> {}", location);
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}