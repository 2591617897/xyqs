package com.campus.admin.config;

import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * 后台密码工具。
 *
 * <p>与 campus-auth 的 {@code AuthService#md5Hex}（小写 32 位 MD5）保持同款实现，
 * 只是额外加了每人一行盐 {@code salt}，避免整个后台共用无盐 md5 被撞库。
 */
@Slf4j
public final class AdminPasswordUtil {

    private AdminPasswordUtil() {
    }

    /** MD5(明文 + salt) 小写 32 位。初始化脚本里用的是 MySQL 的 MD5(CONCAT(明文, salt))，两边必须一致。 */
    public static String md5Hex(String plain, String salt) {
        return md5Hex(plain + salt);
    }

    /** MD5 小写 32 位 */
    public static String md5Hex(String src) {
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
