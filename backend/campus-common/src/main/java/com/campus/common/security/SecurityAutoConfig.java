package com.campus.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把所有服务的登录上下文注入统一在这里，业务代码直接用 UserContext 取值。
 */
@Configuration
public class SecurityAutoConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HeaderUserInterceptor()).addPathPatterns("/**");
    }

    static class HeaderUserInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String userId = request.getHeader("X-User-Id");
            if (StringUtils.hasText(userId)) {
                String schoolId = request.getHeader("X-School-Id");
                String vs = request.getHeader("X-Verify-Status");
                UserContext.set(new UserContext.LoginUser(
                        Long.valueOf(userId),
                        parseLong(schoolId),
                        parseInt(vs)));
            }
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception ex) {
            UserContext.clear();
        }

        private Long parseLong(String v) {
            try {
                return StringUtils.hasText(v) ? Long.valueOf(v) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private Integer parseInt(String v) {
            try {
                return StringUtils.hasText(v) ? Integer.valueOf(v) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
