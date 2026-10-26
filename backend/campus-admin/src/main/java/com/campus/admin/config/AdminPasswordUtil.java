package com.campus.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

/**
 * 后台密码工具。
 *
 * <p><b>为什么从 MD5 换掉</b>：原来的实现是 {@code MD5(明文 + 每人一行盐)}。
 * MD5 是设计给「快速摘要」的，不是给口令用的 —— 单张消费级显卡每秒可以试几十亿次，
 * 一个 8 位口令在离线爆破下基本等于明文；而且项目里所有管理员还共用同一个固定盐，
 * 撞库成本进一步降低。库一旦泄漏（SQL 注入、备份外泄、云盘误传），密码就等同于公开。
 *
 * <p><b>现在</b>：BCrypt（自带随机盐 + 可调工作因子），cost=12 意味着单次校验约 200~400ms，
 * 想暴力尝试就得付出同等算力成本，同时登录体验几乎无感。
 *
 * <p><b>平滑迁移</b>：库里已有的 MD5 记录仍然可以登录，登录成功的那一刻自动改写为 BCrypt
 * （见 {@code AuthAdminService#login}）。不需要停机刷库，也不需要强制全员改密。
 */
@Slf4j
public final class AdminPasswordUtil {

    /** 工作因子。每 +1 耗时翻倍；12 在 2026 年的服务器上约 200~400ms，属于推荐区间。 */
    private static final int BCRYPT_COST = 12;

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(BCRYPT_COST);

    /** 登录口令黑名单：这些值在任何场景下都不允许使用 */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "12345678", "123456789", "1234567890", "password", "password1", "password123",
            "admin123", "admin1234", "admin@123", "admin888", "qwerty123", "abc12345",
            "11111111", "88888888", "66666666", "iloveyou", "campus123", "a1234567");

    private AdminPasswordUtil() {
    }

    /** 生成 BCrypt 哈希（自带盐，不需要再单独存 salt） */
    public static String hash(String plain) {
        return BCRYPT.encode(plain);
    }

    /**
     * 校验口令。
     *
     * @param storedHash 库里的 password（可能是 BCrypt，也可能是历史 MD5）
     * @param legacySalt 历史 MD5 使用的盐，BCrypt 记录可忽略
     */
    public static boolean matches(String plain, String storedHash, String legacySalt) {
        if (plain == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        if (isBcrypt(storedHash)) {
            try {
                return BCRYPT.matches(plain, storedHash);
            } catch (IllegalArgumentException e) {
                log.warn("[security] BCrypt 哈希格式异常，拒绝该次登录");
                return false;
            }
        }
        // 历史格式：MD5(明文 + salt)，小写 32 位
        return md5Hex(plain + (legacySalt == null ? "" : legacySalt))
                .equalsIgnoreCase(storedHash);
    }

    /** 是否需要升级为 BCrypt（登录成功后据此重写密码） */
    public static boolean needsUpgrade(String storedHash) {
        return storedHash != null && !isBcrypt(storedHash);
    }

    public static boolean isBcrypt(String hash) {
        return hash != null && (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
    }

    /**
     * 口令强度校验。返回 null 表示通过，否则是给用户看的原因。
     *
     * <p>规则贴合等保/常见审计口径：8 位起步、至少三类字符、不能是弱口令、
     * 不能包含账号名、不能与旧密码相同（后者由调用方判断）。
     */
    public static String checkStrength(String plain, String username) {
        if (plain == null || plain.length() < 8) {
            return "密码长度不能少于 8 位";
        }
        if (plain.length() > 64) {
            return "密码长度不能超过 64 位";
        }
        String lower = plain.toLowerCase(Locale.ROOT);
        if (WEAK_PASSWORDS.contains(lower)) {
            return "密码过于常见，请换一个";
        }
        if (username != null && !username.isBlank() && lower.contains(username.toLowerCase(Locale.ROOT))) {
            return "密码不能包含账号名";
        }
        int kinds = 0;
        if (plain.chars().anyMatch(Character::isLowerCase)) {
            kinds++;
        }
        if (plain.chars().anyMatch(Character::isUpperCase)) {
            kinds++;
        }
        if (plain.chars().anyMatch(Character::isDigit)) {
            kinds++;
        }
        if (plain.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            kinds++;
        }
        if (kinds < 3) {
            return "密码需包含大写字母、小写字母、数字、符号中的至少三类";
        }
        return null;
    }

    /** MD5 小写 32 位（仅用于校验历史数据，新密码一律 BCrypt） */
    static String md5Hex(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(src.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String h = Integer.toHexString(b & 0xFF);
                if (h.length() == 1) {
                    sb.append('0');
                }
                sb.append(h);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("md5 计算失败", e);
            throw new IllegalStateException("md5 计算失败", e);
        }
    }
}
