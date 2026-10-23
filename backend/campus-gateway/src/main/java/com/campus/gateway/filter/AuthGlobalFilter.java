package com.campus.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局鉴权过滤器：
 * 1. 白名单直接放行（登录、学校字典、内容只读接口）
 * 2. 其余请求必须携带有效 JWT
 * 3. 校验通过后把身份写入下游 Header，并清除客户端伪造的同名 Header
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 完全放行的前缀：登录、学校字典、静态图片、健康检查 */
    private static final List<String> WHITE_PREFIX =
            List.of("/api/auth/", "/api/schools", "/uploads", "/actuator");
    private static final List<String> PUBLIC_READ_PREFIX =
            List.of("/api/goods", "/api/wall", "/api/posts", "/api/channels");
    /** 这些前缀虽然是 GET，但属于「我的」私域数据，必须登录 */
    private static final List<String> PRIVATE_HINTS = List.of("/mine", "/orders", "/favorite");

    private final SecretKey key;

    public AuthGlobalFilter(@Value("${campus.jwt.secret:campus-fun-jwt-secret-key-please-change-in-prod-2026}")
                            String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() == null ? "GET" : request.getMethod().name();

        boolean whitelisted = WHITE_PREFIX.stream().anyMatch(path::startsWith);
        boolean publicRead = "GET".equals(method)
                && PUBLIC_READ_PREFIX.stream().anyMatch(path::startsWith)
                && PRIVATE_HINTS.stream().noneMatch(path::contains);

        Claims claims = null;
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            try {
                claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
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
