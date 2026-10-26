package com.campus.common.crypto;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/**
 * 密钥统一来源。
 *
 * <p><b>为什么要有这个类</b>：以前 JWT 密钥、后台令牌密钥直接写在 application.yml 里带默认值，
 * 仓库一旦外泄，任何人都能自己签发合法令牌（等于没有鉴权）。现在一律按下面的优先级取：
 *
 * <ol>
 *   <li>环境变量 / JVM 参数（生产唯一正确姿势，例如 {@code JWT_SECRET=...}）</li>
 *   <li>{@code ~/.campus-secrets/<fileName>}（本机开发用，多个服务读同一个文件所以天然一致）</li>
 *   <li>都没有 → 首次启动随机生成并落盘，同时打一条 WARN</li>
 * </ol>
 *
 * <p>生产可以设置 {@code CAMPUS_SECRET_STRICT=true} 强制「必须来自环境变量」，
 * 缺了就启动失败 —— 宁可起不来，也不要悄悄用上一台机器残留的密钥。
 */
@Slf4j
public final class SecretStore {

    /** 密钥落盘目录（本机开发用）。各微服务工作目录不同，所以不能放项目内相对路径。 */
    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".campus-secrets");

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretStore() {
    }

    /**
     * 取密钥。
     *
     * @param envKey   环境变量名，如 JWT_SECRET
     * @param fileName 落盘文件名，如 jwt.key（多个服务必须用同一个名字才能共享）
     * @param minBytes 最小字节数（HS256 要求 >= 32）
     * @param purpose  用途描述，只用于日志
     * @return 非空密钥字符串
     */
    public static String resolve(String envKey, String fileName, int minBytes, String purpose) {
        String fromEnv = readExternal(envKey);
        if (fromEnv != null && fromEnv.getBytes(StandardCharsets.UTF_8).length >= minBytes) {
            return fromEnv;
        }
        if (fromEnv != null) {
            throw new IllegalStateException(envKey + " 长度不足 " + minBytes + " 字节（当前 "
                    + fromEnv.getBytes(StandardCharsets.UTF_8).length + "），" + purpose + " 拒绝使用弱密钥启动");
        }
        if (strict()) {
            throw new IllegalStateException("已开启 CAMPUS_SECRET_STRICT，但未提供环境变量 " + envKey
                    + "（用途：" + purpose + "）。请通过环境变量注入密钥后再启动。");
        }

        Path file = DIR.resolve(fileName);
        String fromFile = readFile(file, minBytes);
        if (fromFile != null) {
            return fromFile;
        }
        String generated = generate(minBytes);
        writeFile(file, generated);
        log.warn("[security] 未提供 {}，已为『{}』生成随机密钥并保存到 {}（仅本机有效，多实例部署请改用环境变量）",
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
            if (v.isEmpty()) {
                return null;
            }
            if (v.getBytes(StandardCharsets.UTF_8).length < minBytes) {
                log.warn("[security] 密钥文件 {} 内容过短（< {} 字节），已忽略并重新生成", file, minBytes);
                return null;
            }
            return v;
        } catch (IOException e) {
            log.warn("[security] 读取密钥文件 {} 失败：{}", file, e.getMessage());
            return null;
        }
    }

    private static String generate(int minBytes) {
        byte[] buf = new byte[Math.max(minBytes, 32)];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static void writeFile(Path file, String content) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            restrictPermissions(file);
        } catch (IOException e) {
            // 写不进去不影响本次启动（内存里已有密钥），但下次会换新密钥 —— 必须让人看见
            log.error("[security] 密钥文件写入失败 {}：{}。重启后令牌/密文将无法还原，请尽快改为环境变量注入",
                    file, e.getMessage());
        }
    }

    /** POSIX 平台收紧到 600；Windows 上忽略（依赖用户目录的默认 ACL）。 */
    private static void restrictPermissions(Path file) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 走不到这里
        }
    }
}
