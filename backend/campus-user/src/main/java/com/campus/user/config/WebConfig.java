package com.campus.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
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
        Path abs = UploadPaths.resolve(uploadDir);
        String location = abs.toUri().toString();
        // 关键日志：配置写 ./uploads 时，实际指向由 UploadPaths 统一决定
        // （jar 运行时 = jar 同目录，IDE 运行时 = 进程工作目录）
        log.info("[upload] 静态资源映射 /uploads/** -> {}（配置 dir={}，进程工作目录={}）",
                location, uploadDir, Paths.get("").toAbsolutePath());
        // 启动即校验，避免「上传成功但访问 404」这种最难查的情况
        if (!Files.isDirectory(abs)) {
            log.warn("[upload] 上传目录不存在，将在首次上传时创建: {}（建议改用绝对路径）", abs);
        } else if (!Files.isReadable(abs)) {
            log.error("[upload] 上传目录不可读，/uploads/** 会返回 404: {}（检查属主与权限）", abs);
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}