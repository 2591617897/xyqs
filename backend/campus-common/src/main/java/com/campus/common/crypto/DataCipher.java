package com.campus.common.crypto;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 字段级加密（AES-256-GCM）。
 *
 * <p>用于 t_user 的手机号 / 学号 / 真实姓名、t_goods_order 的联系方式这类
 * 「业务上需要、但一旦整库泄露就是事故」的列。加密在应用层完成，
 * 数据库、备份文件、只读从库拿到的都只是密文。
 *
 * <p>密文格式 {@code enc:v1:<base64(iv|ct|tag)>}，带版本前缀是为了以后换算法能平滑过渡。
 * <b>不带前缀的值一律按历史明文原样返回</b> —— 这样升级当天不需要停机刷库，
 * 老数据读到明文、下一次写回自动变成密文（{@code EncryptedStringTypeHandler} 负责这件事）。
 */
@Slf4j
public final class DataCipher {

    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec key;

    private DataCipher() {
    }

    /**
     * 密钥派生：外部提供的是任意长度字符串（base64 / 口令），统一 SHA-256 成 32 字节。
     * 这样配置侧不需要关心长度，也不会因为随手填 8 个字符就变成弱密钥。
     */
    private static SecretKeySpec key() {
        SecretKeySpec k = key;
        if (k == null) {
            synchronized (DataCipher.class) {
                k = key;
                if (k == null) {
                    String secret = SecretStore.resolve("CAMPUS_DATA_KEY", "data-key.key", 32, "敏感字段加密");
                    k = derive(secret);
                    key = k;
                }
            }
        }
        return k;
    }

    private static SecretKeySpec derive(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段加密密钥派生失败", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * 加密。null / 空串原样返回（不制造 {} 这种无意义密文）。
     */
    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty() || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            // 加密失败宁可抛出去让写入失败，也绝不能退化成写明文
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    /**
     * 解密。历史明文（无前缀）原样返回，保证灰度期读写都正常。
     */
    public static String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (raw.length <= IV_BYTES) {
                log.warn("[security] 密文长度异常，已按 null 处理");
                return null;
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 换过密钥 / 密文被篡改都会走到这里。返回 null 而不是明文或异常，
            // 避免一条坏数据把整个列表接口打成 500。
            log.error("[security] 敏感字段解密失败（密钥是否更换过？）：{}", e.getMessage());
            return null;
        }
    }
}
