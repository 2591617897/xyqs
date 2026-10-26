package com.campus.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.campus.gateway.security.SecretResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 全局鉴权过滤器：
 * 1. 白名单直接放行（登录、学校字典、内容只读接口）
 * 2. 公开读放行：GET /api/user/{userId}/profile 与 GET /api/user/follow/status
 *    （精确路径匹配，不放宽 /api/user 前缀，避免误开 /user/me 等私域接口）
 * 3. 其余请求必须携带有效 JWT
 * 4. 校验通过后把身份写入下游 Header，并清除客户端伪造的同名 Header
 *    （公开读接口若带了有效 token，同样会注入身份，供「是否已关注」判定）
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 完全放行的前缀：登录、学校字典、静态图片 */
    private static final List<String> WHITE_PREFIX =
            List.of("/api/auth/", "/api/schools", "/uploads");
    /**
     * 健康检查只放行精确路径。
     *
     * <p>以前是 {@code /actuator} 前缀全放行：一旦网关引入了 actuator 依赖，
     * {@code /actuator/env}、{@code /actuator/heapdump} 这类端点会把配置与内存直接吐给公网。
     */
    private static final String ACTUATOR_HEALTH_EXACT = "/actuator/health";
    /**
     * 微信消息推送回调（图片内容审核结果）。
     *
     * <p>微信服务器从公网直连，不可能带用户令牌，只能放行；安全性由接口自身保证——
     * /wx/callback 会校验 sha1(token, timestamp, nonce)，Token 不对外泄露。
     * 这条前缀下只有回调一个接口，不放任何业务能力。
     */
    private static final String WX_CALLBACK_PREFIX = "/api/wx/";
    private static final List<String> PUBLIC_READ_PREFIX =
            List.of("/api/goods", "/api/wall", "/api/posts", "/api/channels",
                    // 首页运营位：广告素材与后台公告要在游客首屏就可见，
                    // 否则图片和公告永远只在后台存在、C 端看不到。两者都是只读。
                    "/api/ads", "/api/notices");
    /** 这些前缀虽然是 GET，但属于「我的」私域数据，必须登录 */
    private static final List<String> PRIVATE_HINTS = List.of("/mine", "/orders", "/favorite");
    /**
     * 公开个人主页：GET /api/user/{userId}/profile（userId 为纯数字）。
     * 只放行精确匹配，不放宽 /api/user 前缀——否则 /user/me 等私域接口会被误开。
     */
    private static final Pattern PUBLIC_PROFILE = Pattern.compile("^/api/user/\\d+/profile$");
    /**
     * 广告点击上报：未登录用户点了 Banner 也该计入效果数据（否则投放转化率永远偏低），
     * 但这是<b>写操作</b>，不能随 /api/ads 前缀一起进 PUBLIC_READ_PREFIX（那里只放行 GET）。
     * 所以用正则精确匹配一个形状：/api/ads/materials/数字/click。
     */
    private static final Pattern PUBLIC_AD_CLICK =
            Pattern.compile("^/api/ads/materials/\\d+/click$");
    /**
     * 公告弹窗的「我知道了」回执。
     *
     * <p>同样是写操作，同样必须让游客可用：游客没有令牌，服务端拿到不到 user_id 时
     * 直接 no-op（不落库），确认状态由小程序本地存储兜住。若这里要求登录，
     * 游客点「我知道了」会拿到 401，弹窗关不掉——这比不弹更烦人。
     * 只放行「数字 id + confirm」这一种形状，不放开 /api/notices 下的其它写接口。
     */
    private static final Pattern PUBLIC_NOTICE_CONFIRM =
            Pattern.compile("^/api/notices/\\d+/confirm$");

    /**
     * 后台管理路径前缀。
     *
     * <p><strong>严禁把 ADMIN_PREFIX 加进上面的 WHITE_PREFIX</strong>：那样后台所有接口都会裸奔。
     * 后台唯一不需要令牌的接口只有登录，且必须「方法 + 路径」双精确匹配。
     */
    private static final String ADMIN_PREFIX = "/api/admin";
    private static final String ADMIN_LOGIN_EXACT = ADMIN_PREFIX + "/auth/login";
    /** 后台令牌里的身份类型标记，用于防止小程序用户令牌串用进后台。 */
    private static final String ADMIN_TOKEN_TYPE = "admin";

    /** 令牌签发方 / 受众，与 campus-common 的 JwtService、campus-admin 的 AdminJwtService 保持一字不差 */
    private static final String ISSUER = "campus-api";
    private static final String AUDIENCE_MINIAPP = "campus-miniapp";
    private static final String AUDIENCE_ADMIN = "campus-admin";

    private final SecretKey key;
    /** 后台管理员令牌专用密钥，与 C 端 campus.jwt.secret 完全隔离。 */
    private final SecretKey adminKey;

    public AuthGlobalFilter(@Value("${campus.jwt.secret:}") String secret,
                            @Value("${campus.admin.jwt.secret:}") String adminSecret) {
        // 密钥不再允许「配置里没写就用一个写死的字符串」：
        // 空值时统一到 SecretResolver（环境变量 → ~/.campus-secrets/ → 随机生成落盘），
        // 与各业务服务、campus-admin 读的是同一份，才能互验签名。
        String userKey = StringUtils.hasText(secret) && secret.getBytes(StandardCharsets.UTF_8).length >= 32
                ? secret.trim()
                : SecretResolver.resolve("JWT_SECRET", "jwt.key", 32, "C 端登录令牌");
        String adminKeyValue = StringUtils.hasText(adminSecret)
                && adminSecret.getBytes(StandardCharsets.UTF_8).length >= 32
                ? adminSecret.trim()
                : SecretResolver.resolve("ADMIN_JWT_SECRET", "admin-jwt.key", 32, "后台管理令牌");
        this.key = Keys.hmacShaKeyFor(userKey.getBytes(StandardCharsets.UTF_8));
        this.adminKey = Keys.hmacShaKeyFor(adminKeyValue.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() == null ? "GET" : request.getMethod().name();

        // 浏览器 CORS 预检不带凭证，必须放行。
        // 管理台是浏览器应用且带 Authorization 头，跨域时会先发 OPTIONS；
        // CorsWebFilter 是 WebFilter，理论上先于本 GlobalFilter 执行并直接 200 短路，
        // 但网关链路顺序随版本可能变化，这里做一道防御，避免预检被打成 401 导致管理台全站跨域失败。
        if ("OPTIONS".equals(method)) {
            return chain.filter(exchange);
        }

        boolean whitelisted = WHITE_PREFIX.stream().anyMatch(path::startsWith)
                // 健康检查只认精确路径，其余 /actuator/** 一律要求令牌
                || ACTUATOR_HEALTH_EXACT.equals(path)
                // 微信消息推送回调：只放行 GET/POST /api/wx/callback 这一条精确路径，
                // 用前缀 + 精确路径双条件，避免以后 /api/wx 下新增接口被顺带放开
                || (path.startsWith(WX_CALLBACK_PREFIX) && path.endsWith("/callback"));
        // 公开读：GET /api/user/{数字}/profile 与 GET /api/user/follow/status（精确匹配，全等）
        boolean publicProfile = "GET".equals(method) && PUBLIC_PROFILE.matcher(path).matches();
        boolean publicFollowStatus = "GET".equals(method) && "/api/user/follow/status".equals(path);
        boolean publicAdClick = "POST".equals(method) && PUBLIC_AD_CLICK.matcher(path).matches();
        boolean publicNoticeConfirm = "POST".equals(method) && PUBLIC_NOTICE_CONFIRM.matcher(path).matches();
        boolean publicRead = publicProfile || publicFollowStatus || publicAdClick || publicNoticeConfirm
                || ("GET".equals(method)
                && PUBLIC_READ_PREFIX.stream().anyMatch(path::startsWith)
                && PRIVATE_HINTS.stream().noneMatch(path::contains));

        // 后台管理：独立令牌体系（campus.admin.jwt.secret），除登录外全部要求管理员令牌
        if (path.startsWith(ADMIN_PREFIX)) {
            if ("POST".equals(method) && ADMIN_LOGIN_EXACT.equals(path)) {
                return chain.filter(exchange.mutate().request(stripAdminHeaders(request).build()).build());
            }
            Claims adminClaims = parse(resolveToken(request), adminKey);
            if (adminClaims == null) {
                return unauthorized(exchange, "请先登录管理后台");
            }
            // 防 C 端令牌串用：即使密钥泄漏，没有 typ=admin 也进不了后台
            if (!ADMIN_TOKEN_TYPE.equals(String.valueOf(adminClaims.get("typ")))) {
                return unauthorized(exchange, "凭证类型不正确");
            }
            ServerHttpRequest.Builder adminBuilder = stripAdminHeaders(request);
            adminBuilder.header("X-Admin-Id", adminClaims.getSubject());
            // 账号名可能含中文，Header 只允许 ASCII，统一 URL 编码后再传
            adminBuilder.header("X-Admin-Username", urlEncode(str(adminClaims.get("username"))));
            adminBuilder.header("X-Admin-Super", str(adminClaims.get("super")));
            adminBuilder.header("X-Admin-Scope", str(adminClaims.get("scope")));
            adminBuilder.header("X-Admin-Regions", joinRegions(adminClaims.get("regions")));
            return chain.filter(exchange.mutate().request(adminBuilder.build()).build());
        }

        Claims claims = null;
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            try {
                claims = Jwts.parser()
                        .verifyWith(key)
                        .requireIssuer(ISSUER)
                        .requireAudience(AUDIENCE_MINIAPP)
                        .build()
                        .parseSignedClaims(token).getPayload();
            } catch (Exception e) {
                log.debug("token 解析失败: {}", e.getMessage());
                if (!whitelisted && !publicRead) {
                    return unauthorized(exchange, "登录已过期，请重新登录");
                }
            }
        }
        if (claims == null && !whitelisted && !publicRead) {
            return unauthorized(exchange, "请先登录");
        }

        ServerHttpRequest.Builder builder = request.mutate();
        builder.headers(h -> {
            // 防止客户端伪造身份头
            h.remove("X-User-Id");
            h.remove("X-School-Id");
            h.remove("X-Verify-Status");
        });
        if (claims != null) {
            builder.header("X-User-Id", claims.getSubject());
            Object schoolId = claims.get("schoolId");
            if (schoolId != null) {
                builder.header("X-School-Id", String.valueOf(schoolId));
            }
            Object vs = claims.get("vs");
            if (vs != null) {
                builder.header("X-Verify-Status", String.valueOf(vs));
            }
        }
        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    /**
     * 解析令牌，失败返回 null（admin 与 user 两套密钥不能混用）。
     */
    private Claims parse(String token, SecretKey secretKey) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(ISSUER)
                    .requireAudience(AUDIENCE_ADMIN)
                    .build()
                    .parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            log.debug("token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 后台请求的身份头一律由本过滤器写入。先清除客户端可能伪造的同名头，
     * 与 C 端 X-User-Id 的处理方式保持一致。
     */
    private ServerHttpRequest.Builder stripAdminHeaders(ServerHttpRequest request) {
        ServerHttpRequest.Builder builder = request.mutate();
        builder.headers(h -> {
            h.remove("X-Admin-Id");
            h.remove("X-Admin-Username");
            h.remove("X-Admin-Super");
            h.remove("X-Admin-Scope");
            h.remove("X-Admin-Regions");
        });
        return builder;
    }

    private String urlEncode(String v) {
        if (!StringUtils.hasText(v)) {
            return "";
        }
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * claim 里的辖区 id 集合转逗号分隔字符串；空字符串表示「不限辖区」。
     */
    private String joinRegions(Object regions) {
        if (!(regions instanceof java.util.Collection<?> list) || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : list) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(o);
        }
        return sb.toString();
    }

    private String resolveToken(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return request.getHeaders().getFirst("X-Token");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
