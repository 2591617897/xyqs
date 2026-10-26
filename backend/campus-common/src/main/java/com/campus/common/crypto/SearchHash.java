package com.campus.common.crypto;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

/**
 * 盲索引（blind index）：让「加密后的列」仍然可以被等值查询。
 *
 * <p>场景：学号要唯一，认证时得判断「这个学校这个学号是否已被占用」。
 * 学号列既然已经 AES-GCM 加密（随机 IV，同一明文两次加密结果不同），
 * 就没法再用 {@code where student_no = ?} 了。
 *
 * <p>做法是额外存一列 HMAC-SHA256 摘要：固定密钥 + 归一化明文 → 稳定摘要，
 * 只用于等值比对。密钥在应用侧，<b>数据库泄露也无法反推学号</b>（这是它比
 * 无密钥 SHA-256 强的地方：学号空间很小，裸哈希可以被穷举）。
 */
@Slf4j
public final class SearchHash {

    private static final String HMAC = "HmacSHA256";
    /** 领域分隔前缀，避免同一个密钥在不同业务字段之间产生可关联的摘要 */
    private static final String CONTEXT = "campus:blind-index:v1:";

    private static volatile SecretKeySpec key;

    private SearchHash() {
    }

    private static SecretKeySpec key() {
        SecretKeySpec k = key;
        if (k == null) {
            synchronized (SearchHash.class) {
                k = key;
                if (k == null) {
                    String secret = SecretStore.resolve("CAMPUS_DATA_KEY", "data-key.key", 32, "盲索引");
                    try {
                        // 与字段加密分开派生：同一口令、不同 context，得到互不相同的密钥
                        byte[] digest = MessageDigest.getInstance("SHA-256")
                                .digest(("blind-index|" + secret).getBytes(StandardCharsets.UTF_8));
                        k = new SecretKeySpec(digest, HMAC);
                        key = k;
                    } catch (Exception e) {
                        throw new IllegalStateException("盲索引密钥派生失败", e);
                    }
                }
            }
        }
        return k;
    }

    /**
     * 计算盲索引。
     *
     * @param scope 作用域（用学校 id，避免不同学校同号互相冲突）
     * @param value 明文（会 trim + 转小写做归一化）
     * @return 十六进制摘要；value 为空时返回 null
     */
    public static String of(String scope, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String msg = CONTEXT + (scope == null ? "" : scope) + "|"
                + value.trim().toLowerCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key());
            byte[] out = mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("盲索引计算失败", e);
        }
    }
}
