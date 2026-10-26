package com.campus.gateway.security;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 网关侧的密钥解析（campus-common 里 SecretStore 的精简版）。
 *
 * <p>为什么不直接依赖 campus-common：那个模块带 {@code spring-boot-starter-web}，
 * 引进来会让 Spring Cloud Gateway 检测到 Spring MVC 而拒绝启动（响应式与 Servlet 不能共存）。
 * 所以这里保留一份同样规则的实现，<b>文件名与优先级必须与 common 保持一致</b>：
 * 环境变量 → {@code ~/.campus-secrets/<fileName>} → 随机生成落盘。
 */
@Slf4j
public final class SecretResolver {

    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".campus-secrets");
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretResolver() {
    }

    public static String resolve(String envKey, String fileName, int minBytes, String purpose) {
        String fromEnv = readExternal(envKey);
        if (fromEnv != null) {
            if (fromEnv.getBytes(StandardCharsets.UTF_8).length < minBytes) {
                throw new IllegalStateException(envKey + " 长度不足 " + minBytes + " 字节（" + purpose + "）");
            }
            return fromEnv;
        }
        if (strict()) {
            throw new IllegalStateException("已开启 CAMPUS_SECRET_STRICT，但未提供环境变量 " + envKey
                    + "（用途：" + purpose + "）");
        }
        Path file = DIR.resolve(fileName);
        String fromFile = readFile(file, minBytes);
        if (fromFile != null) {
            return fromFile;
        }
        byte[] buf = new byte[Math.max(minBytes, 32)];
        RANDOM.nextBytes(buf);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        try {
            Files.createDirectories(DIR);
            Files.writeString(file, generated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[security] 密钥文件写入失败 {}：{}", file, e.getMessage());
        }
        log.warn("[security] 未提供 {}，已为『{}』生成随机密钥并保存到 {}（多实例部署请改用环境变量）",
                envKey, purpose, file);
        return generated;
    }

    private static boolean strict() {
        String v = readExternal("CAMPUS_SECRET_STRICT");
        return v != null && ("true".equalsIgnoreCase(v) || "1".equals(v));
    }

    private static String readExternal(String key) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(key);
        }
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private static String readFile(Path file, int minBytes) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            String v = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (v.isEmpty() || v.getBytes(StandardCharsets.UTF_8).length < minBytes) {
                return null;
            }
            return v;
        } catch (IOException e) {
            log.warn("[security] 读取密钥文件 {} 失败：{}", file, e.getMessage());
            return null;
        }
    }
}
