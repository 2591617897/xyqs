package com.campus.common.security;

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

/**
 * JWT 签发与解析。网关负责校验，业务服务只读网关注入的身份。
 *
 * <p><b>密钥来源</b>：不再有硬编码默认值。配置 {@code campus.jwt.secret} 为空时，
 * 从 {@link SecretStore} 取（环境变量 JWT_SECRET → ~/.campus-secrets/jwt.key → 随机生成并落盘）。
 * 密钥随代码进仓库是历史遗留问题里最严重的一个：拿到仓库的人可以自己签一个
 * {@code sub=9001} 的令牌直接冒充任意用户，所有权限校验都白写。
 *
 * <p>令牌里除身份外还带 {@code iss/aud/typ}：issuer 与 audience 防止本系统令牌被
 * 拿到别的系统复用（反之亦然），typ 让 C 端令牌和后台令牌在数据结构上就分得开。
 */
@Component
public class JwtService {

    public static final String ISSUER = "campus-api";
    public static final String AUDIENCE = "campus-miniapp";
    public static final String TYPE_USER = "user";

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(@Value("${campus.jwt.secret:}") String secret,
                      @Value("${campus.jwt.expire-hours:72}") long expireHours) {
        String effective = StringUtils.hasText(secret) && secret.getBytes(StandardCharsets.UTF_8).length >= 32
                ? secret.trim()
                : SecretStore.resolve("JWT_SECRET", "jwt.key", 32, "C 端登录令牌");
        this.key = Keys.hmacShaKeyFor(effective.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    public long expireHours() {
        return expireMillis / 3600_000L;
    }

    /**
     * 签发 C 端令牌。
     *
     * <p><b>{@code tokenVersion} 为什么是必填</b>：JWT 无状态，封禁 / 注销只是改库，
     * 对已签发的令牌毫无影响——这是第三轮审计的 P0-1。把 {@code t_user.token_version}
     * 写进 claim {@code tv}，网关比对库里的值就能让旧令牌立刻失效；
     * 封禁、解封、注销、恢复各 +1。
     *
     * <p>故意<b>不提供 3 参数重载</b>：那种便利写法一旦被新代码调用，
     * 就会签出一张 {@code tv=0} 的令牌，对 {@code token_version} 已经 +1 的用户
     * 表现为「刚登录就被踢」。少一个重载，编译器会替我们守住这个坑。
     *
     * @param tokenVersion 账号当前的令牌版本，null 视为 0（与库默认值一致）
     */
    public String create(Long userId, Long schoolId, Integer verifyStatus, Integer tokenVersion) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("typ", TYPE_USER)
                .claim("schoolId", schoolId)
                .claim("vs", verifyStatus)
                .claim("tv", tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验，失败抛异常（由网关捕获转 401）。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
