package com.campus.admin.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * 后台管理员令牌签发。
 *
 * <p><strong>与 C 端令牌是两套密钥</strong>：C 端用 {@code campus.jwt.secret}，后台用
 * {@code campus.admin.jwt.secret}，网关也配了同一份；再加 claim {@code typ=admin}
 * 做二次校验，防止小程序用户令牌串用进后台。
 *
 * <p>注意 jjwt 的 {@code Keys.hmacShaKeyFor} 要求 HS256 密钥不低于 32 字节，
 * 配置写短了会在服务启动时直接抛 WeakKeyException。
 */
@Component
public class AdminJwtService {

    private final SecretKey key;
    private final long expireMillis;

    public AdminJwtService(
            @Value("${campus.admin.jwt.secret:campus-admin-jwt-secret-key-change-me-please-32+}") String secret,
            @Value("${campus.admin.jwt.expire-hours:8}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    public long expireHours() {
        return expireMillis / 3600_000L;
    }

    /**
     * @param regions 辖区节点 id 列表（t_admin_region.id）；数据权限为「全部」时传空集合
     */
    public String create(Long adminId, String username, boolean superAdmin, String scope, List<Long> regions) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .claim("typ", "admin")
                .claim("username", username)
                .claim("super", superAdmin ? 1 : 0)
                .claim("scope", scope)
                .claim("regions", regions == null ? List.of() : regions)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析失败抛异常（本项目约定由上层转成 401）。
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
