package com.campus.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@EnableDiscoveryClient
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * 跨域策略。
     *
     * <p>小程序端不走浏览器 CORS，只有管理台（浏览器）需要。以前是
     * {@code allowedOriginPatterns("*") + allowCredentials(true)}：
     * 任意网站都能从受害者浏览器里带上凭证调用后台接口（CSRF 类风险），
     * 配了凭证的 * 本身也是浏览器规范里明确禁止的组合，属于「看起来能跑、实则危险」的配置。
     *
     * <p>现在改为来源白名单 + 关闭凭证携带（管理台用 Authorization: Bearer，本来就不依赖 Cookie）。
     * 白名单通过 {@code campus.cors.allowed-origins} 或环境变量 CORS_ALLOWED_ORIGINS 配置。
     */
    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${campus.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
            String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Token", "X-Requested-With"));
        // Bearer 令牌不依赖浏览器自动携带的 Cookie，关掉凭证可显著降低 CSRF 面
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
