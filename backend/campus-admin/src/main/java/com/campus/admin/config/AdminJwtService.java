package com.campus.admin.config;

import com.campus.common.crypto.SecretStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
 * <p>密钥同样不再有硬编码默认值：{@code ADMIN_JWT_SECRET} → {@code ~/.campus-secrets/admin-jwt.key}
 * → 随机生成。令牌带 {@code iss=campus-api} / {@code aud=campus-admin}，
 * 网关校验时两个条件都要满足。
 */
@Component
public class AdminJwtService {

    public static final String ISSUER = "campus-api";
    public static final String AUDIENCE = "campus-admin";

    private final SecretKey key;
    private final long expireMillis;

    public AdminJwtService(
            @Value("${campus.admin.jwt.secret:}") String secret,
            @Value("${campus.admin.jwt.expire-hours:8}") long expireHours) {
        String effective = StringUtils.hasText(secret) && secret.getBytes(StandardCharsets.UTF_8).length >= 32
                ? secret.trim()
                : SecretStore.resolve("ADMIN_JWT_SECRET", "admin-jwt.key", 32, "后台管理令牌");
        this.key = Keys.hmacShaKeyFor(effective.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    public long expireHours() {
        return expireMillis / 3600_000L;
    }

    /**
     * @param regions      辖区节点 id 列表（t_admin_region.id）；数据权限为「全部」时传空集合
     * @param tokenVersion 账号当前的令牌版本，写进 claim {@code tv}；库里的值一变令牌立即失效
     */
    public String create(Long adminId, String username, boolean superAdmin, String scope,
                         List<Long> regions, int tokenVersion) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("typ", "admin")
                .claim("username", username)
                .claim("super", superAdmin ? 1 : 0)
                .claim("scope", scope)
                .claim("regions", regions == null ? List.of() : regions)
                .claim("tv", tokenVersion)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析失败抛异常（本项目约定由上层转成 401）。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token).getPayload();
    }
}
