package com.campus.admin.config;

import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.service.AdminUserService;
import com.campus.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 后台鉴权拦截器：一个 preHandle 里做完「注入上下文 + 接口级权限校验」。
 *
 * <p>用拦截器而不是 AOP 切面，是因为 {@code preHandle} 能直接拿到 {@link HandlerMethod}，
 * 一次把注解读出来，不必再走一层代理。
 *
 * <p>身份信息全部来自网关注入的 {@code X-Admin-*} 头。网关在注入前已经把客户端
 * 伪造的同名头删掉了，所以这里读到的值是可信的；但服务若不经过网关被直连调用，
 * 头就是空的 —— 空头一律按未登录处理，不会出现「绕过网关即可冒充管理员」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    /** 权限缓存。后台 QPS 极低，60 秒 TTL 足够；不引 Redis 是为了少一项基础设施。 */
    private static final long PERM_CACHE_TTL_MS = 60_000L;
    private static final Map<Long, PermCache> PERM_CACHE = new ConcurrentHashMap<>();

    private final AdminUserService adminUserService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        String adminId = request.getHeader("X-Admin-Id");
        if (!StringUtils.hasText(adminId)) {
            throw BizException.unauthorized();
        }
        long id = Long.parseLong(adminId);
        boolean superAdmin = "1".equals(request.getHeader("X-Admin-Super"));
        String scope = StringUtils.hasText(request.getHeader("X-Admin-Scope"))
                ? request.getHeader("X-Admin-Scope") : "REGION";
        List<Long> regions = splitLongs(request.getHeader("X-Admin-Regions"));
        Set<String> perms = superAdmin ? Set.of() : loadPermissions(id);

        AdminContext.set(new AdminContext.AdminUser(
                id, decodeUsername(request.getHeader("X-Admin-Username")),
                superAdmin, scope, regions, perms));

        RequirePermission ann = hm.getMethodAnnotation(RequirePermission.class);
        if (ann == null) {
            ann = hm.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (ann == null || ann.value().length == 0) {
            return true;
        }
        if (superAdmin) {
            return true;
        }
        boolean pass = ann.logical() == RequirePermission.Logical.OR
                ? Arrays.stream(ann.value()).anyMatch(perms::contains)
                : Arrays.stream(ann.value()).allMatch(perms::contains);
        if (!pass) {
            log.warn("权限不足 adminId={} uri={} need={}", id, request.getRequestURI(), Arrays.toString(ann.value()));
            throw BizException.forbidden("没有权限执行该操作");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AdminContext.clear();
    }

    private Set<String> loadPermissions(Long adminId) {
        PermCache cache = PERM_CACHE.get(adminId);
        long now = System.currentTimeMillis();
        if (cache != null && now - cache.at < PERM_CACHE_TTL_MS) {
            return cache.perms;
        }
        Set<String> perms = adminUserService.permissionsOf(adminId);
        PERM_CACHE.put(adminId, new PermCache(perms, now));
        return perms;
    }

    /** 权限变更后立即失效某个管理员的本地缓存，避免最长要等 60 秒才生效 */
    public static void evictPermissionCache(Long adminId) {
        PERM_CACHE.remove(adminId);
    }

    /** 网关用 URLEncoder 编码过（Header 只允许 ASCII，而账号名可能含中文） */
    private String decodeUsername(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private List<Long> splitLongs(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
    }

    private record PermCache(Set<String> perms, long at) {
    }
}
