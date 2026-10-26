package com.campus.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 统一安全响应头。
 *
 * <p>这些头不解决「谁能访问」，但能挡掉一整类低级攻击：
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} —— 上传目录里混进一个 .html/.js，
 *       浏览器也不会把它当脚本执行（配合上传白名单与魔数校验一起用）</li>
 *   <li>{@code X-Frame-Options: DENY} —— 禁止被第三方站点 iframe 嵌套点击劫持</li>
 *   <li>{@code Referrer-Policy} —— 跳转外链时不把带 userId 的地址泄露出去</li>
 *   <li>{@code Cache-Control: no-store}（仅 /api/）—— 个人信息、订单、私信别留在
 *       公共电脑/代理缓存里</li>
 * </ul>
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();

        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        // 接口响应一律不缓存；图片等静态资源走 /uploads（由 Nginx 直出，带 30 天缓存）
        if (request.getPath().value().startsWith("/api/")) {
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 在鉴权过滤器外层执行：401 响应也能带上这些头
        return -110;
    }
}
