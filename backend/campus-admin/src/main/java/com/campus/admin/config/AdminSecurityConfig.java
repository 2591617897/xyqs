package com.campus.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册后台鉴权拦截器。
 *
 * <p>登录接口由网关精确放行且不在这里注册之前就会被排除——它压根没有管理员头，
 * 走不到需要身份的逻辑；列在 exclude 里是为了双重保险。
 */
@Configuration
@RequiredArgsConstructor
public class AdminSecurityConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/**")
                // 免登录的只有三条：登录 + 找回密码的两步。
                // 它们由网关按「POST + 完整路径」精确放行（AuthGlobalFilter#ADMIN_PUBLIC_POST），
                // 这里同步排除，否则请求进了服务也会被拦成 401。
                .excludePathPatterns("/auth/login",
                        "/auth/find-password/code",
                        "/auth/find-password/reset",
                        "/error", "/actuator/**");
    }
}
