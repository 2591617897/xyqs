package com.campus.common.security;

import com.campus.common.exception.BizException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把所有服务的登录上下文注入统一在这里，业务代码直接用 UserContext 取值。
 *
 * <p><b>安全要点（2026-10 加固）</b>：以前这里无条件相信网关注入的 {@code X-User-Id}。
 * 问题在于这些服务端口（8101~8107）本身也在监听，只要能直连到其中一个端口，
 * 加一个 {@code X-User-Id: 9001} 就能以任意用户身份调用接口，网关的 JWT 校验被完全绕过。
 *
 * <p>现在的规则是：<b>身份只从请求自带的 JWT 里取</b>，请求头只作为「有没有身份」的信号。
 * <ul>
 *   <li>带有效令牌 → 用令牌里的 subject / schoolId / vs 覆盖任何请求头</li>
 *   <li>没令牌但带了 X-User-Id → 判定为伪造，直接 401</li>
 *   <li>既没令牌也没头 → 游客，放行（公开读接口本来就不需要登录）</li>
 * </ul>
 * 攻击者没有 JWT 密钥就签不出令牌，直连端口也冒充不了任何人；正常请求经过网关时
 * Authorization 头是原样转发的，所以这条链路对业务完全无感。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityAutoConfig implements WebMvcConfigurer {

    private static final String HEADER_USER_ID = "X-User-Id";

    private final JwtService jwtService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HeaderUserInterceptor(jwtService)).addPathPatterns("/**");
    }

    @RequiredArgsConstructor
    static class HeaderUserInterceptor implements HandlerInterceptor {

        private final JwtService jwtService;

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String token = resolveToken(request);
            Claims claims = null;
            if (StringUtils.hasText(token)) {
                try {
                    claims = jwtService.parse(token);
                } catch (Exception e) {
                    // 过期 / 签名不对 / 是我们换了 issuer 之前的旧令牌，都按「无身份」处理
                    log.debug("令牌解析失败 {}：{}", request.getRequestURI(), e.getMessage());
                }
            }

            if (claims != null) {
                String subject = claims.getSubject();
                if (StringUtils.hasText(subject)) {
                    String headerUserId = request.getHeader(HEADER_USER_ID);
                    if (StringUtils.hasText(headerUserId) && !headerUserId.equals(subject)) {
                        // 有令牌却把用户头改成别人：只可能是伪造或中间件出错，记下来
                        log.warn("[security] 用户头与令牌不一致，已忽略用户头 uri={} header={} token={}",
                                request.getRequestURI(), headerUserId, subject);
                    }
                    UserContext.set(new UserContext.LoginUser(
                            Long.valueOf(subject),
                            toLong(claims.get("schoolId")),
                            toInt(claims.get("vs"))));
                }
                return true;
            }

            // 没有有效令牌，却带着身份头 —— 只有绕过网关直连服务端口才会出现
            if (StringUtils.hasText(request.getHeader(HEADER_USER_ID))) {
                log.warn("[security] 拒绝伪造身份头 uri={} ip={} header={}",
                        request.getRequestURI(), request.getRemoteAddr(), request.getHeader(HEADER_USER_ID));
                throw new BizException(401, "身份校验失败，请重新登录");
            }
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception ex) {
            UserContext.clear();
        }

        private String resolveToken(HttpServletRequest request) {
            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
                return auth.substring(7).trim();
            }
            String xToken = request.getHeader("X-Token");
            return StringUtils.hasText(xToken) ? xToken.trim() : null;
        }

        private Long toLong(Object v) {
            if (v == null) {
                return null;
            }
            try {
                return Long.valueOf(String.valueOf(v));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private Integer toInt(Object v) {
            if (v == null) {
                return null;
            }
            try {
                return Integer.valueOf(String.valueOf(v));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
