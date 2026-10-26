package com.campus.admin.config;

import com.campus.admin.config.annotation.RequirePermission;
import com.campus.admin.dto.AdminAuthState;
import com.campus.admin.service.AdminUserService;
import com.campus.common.exception.BizException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * <p><b>2026-10 加固</b>：以前身份完全来自网关注入的 {@code X-Admin-*} 头，只要直连
 * campus-admin 的 8107 端口（或网关路由配错）就能自己塞一个
 * {@code X-Admin-Id: 900000000000000001 / X-Admin-Super: 1} 当超管。
 * 现在必须同时满足三个条件才认：
 * <ol>
 *   <li>请求自带 Authorization，且能用后台专用密钥验签（{@link AdminJwtService}）</li>
 *   <li>令牌 {@code typ=admin}（C 端令牌密钥不同，天然过不了）</li>
 *   <li>令牌 subject 与 {@code X-Admin-Id} 一致</li>
 * </ol>
 * 而且「是否超管、数据权限、辖区」一律以令牌里的 claim 为准，请求头只用于一致性核对 ——
 * 攻击者改不了签名内容，也就改不了自己的权限。</p>
 *
 * <p><b>令牌吊销</b>：JWT 无状态，签出去就收不回。这里额外比对库里的 {@code token_version}
 * 与令牌 claim {@code tv}：改密、重置、禁用都会让库里的值 +1，旧令牌下一次请求即失效。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    /** 权限缓存。后台 QPS 极低，60 秒 TTL 足够；不引 Redis 是为了少一项基础设施。 */
    private static final long PERM_CACHE_TTL_MS = 60_000L;
    private static final Map<Long, PermCache> PERM_CACHE = new ConcurrentHashMap<>();

    /**
     * 「必须先改初始密码」状态下仍然放行的接口（按 URI 后缀匹配）。
     * 登录、查自身信息、改密、登出、拉菜单 —— 少一个都会把人锁在门外。
     */
    private static final List<String> PWD_CHANGE_ALLOWED = List.of(
            "/auth/info", "/auth/password", "/auth/logout", "/auth/menus");

    private final AdminUserService adminUserService;
    private final AdminJwtService adminJwtService;

    /**
     * 是否强制「初始密码必须改掉才能用后台」。默认 true。
     *
     * <p>只给本地演示留一个逃生阀（{@code CAMPUS_ADMIN_FORCE_PWD_CHANGE=false}）：
     * 演示账号每次都要改密很烦。生产不要关 —— 关了就等于把 admin123 这类初始口令永久开放。
     */
    @Value("${campus.admin.force-password-change:true}")
    private boolean forcePasswordChange;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        String headerAdminId = request.getHeader("X-Admin-Id");
        if (!StringUtils.hasText(headerAdminId)) {
            throw BizException.unauthorized();
        }

        Claims claims = parseAdminToken(request);
        if (claims == null) {
            // 没令牌（直连服务端口）或令牌已失效
            log.warn("[security] 后台请求缺少有效管理员令牌 uri={} ip={}",
                    request.getRequestURI(), request.getRemoteAddr());
            throw BizException.unauthorized();
        }
        if (!headerAdminId.equals(claims.getSubject())) {
            log.warn("[security] 后台身份头与令牌不一致 uri={} header={} token={}",
                    request.getRequestURI(), headerAdminId, claims.getSubject());
            throw BizException.unauthorized();
        }

        long id = Long.parseLong(headerAdminId);

        // ① 账号仍然存在且未被禁用 ② 令牌版本号与库里一致（改密/被踢后旧令牌失效）
        AdminAuthState state = loadAuthState(id);
        if (state == null) {
            log.warn("[security] 令牌对应的管理员已不存在 adminId={}", id);
            throw BizException.unauthorized("登录状态已失效，请重新登录");
        }
        if (state.getStatus() == null || state.getStatus() != 1) {
            log.warn("[security] 已禁用账号尝试访问 adminId={} uri={}", id, request.getRequestURI());
            throw BizException.unauthorized("账号已被禁用，请联系管理员");
        }
        int dbVersion = state.getTokenVersion() == null ? 0 : state.getTokenVersion();
        int tokenVersion = toInt(claims.get("tv"));
        if (dbVersion != tokenVersion) {
            log.warn("[security] 令牌版本已失效 adminId={} token={} db={}", id, tokenVersion, dbVersion);
            throw BizException.unauthorized("登录状态已失效，请重新登录");
        }

        // 权限相关的一切都读令牌，不读请求头
        boolean superAdmin = "1".equals(str(claims.get("super")));
        String scope = StringUtils.hasText(str(claims.get("scope"))) ? str(claims.get("scope")) : "REGION";
        List<Long> regions = toLongs(claims.get("regions"));
        Set<String> perms = superAdmin ? Set.of() : loadPermissions(id);

        AdminContext.set(new AdminContext.AdminUser(
                id, decodeUsername(str(claims.get("username"))),
                superAdmin, scope, regions, perms));

        // ③ 初始密码未改：只放行改密链路，其余接口一律拦住。
        //    以前这一步只靠前端弹窗，只要绕过前端直接调接口，初始口令就能长期使用。
        if (forcePasswordChange
                && state.getMustChangePwd() != null && state.getMustChangePwd() == 1
                && PWD_CHANGE_ALLOWED.stream().noneMatch(request.getRequestURI()::endsWith)) {
            log.warn("[security] 管理员 {} 未修改初始密码即访问 {}，已拦截", id, request.getRequestURI());
            throw BizException.forbidden("请先修改初始密码后再使用后台");
        }

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

    private Claims parseAdminToken(HttpServletRequest request) {
        String token = null;
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            token = auth.substring(7).trim();
        } else if (StringUtils.hasText(request.getHeader("X-Token"))) {
            token = request.getHeader("X-Token").trim();
        }
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = adminJwtService.parse(token);
            return "admin".equals(str(claims.get("typ"))) ? claims : null;
        } catch (Exception e) {
            log.debug("管理员令牌解析失败：{}", e.getMessage());
            return null;
        }
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

    private AdminAuthState loadAuthState(Long adminId) {
        // 不做本地缓存：这里查的是主键上的三列，成本可忽略，而「改完密码立刻失效」
        // 是安全语义，缓存带来的 10 秒窗口不值得换这点性能。
        return adminUserService.authStateOf(adminId);
    }

    /**
     * 权限变更后立即失效本地缓存，避免最长要等 60 秒才生效。
     * 注意：只在当前进程内有效，多实例部署需要额外的广播（或用 Redis 存版本号）。
     */
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

    private List<Long> toLongs(Object regions) {
        if (!(regions instanceof java.util.Collection<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>(list.size());
        for (Object o : list) {
            try {
                out.add(Long.valueOf(String.valueOf(o)));
            } catch (NumberFormatException ignored) {
                // 忽略非法项
            }
        }
        return out;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private int toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            // 老令牌没有 tv claim，按 0 处理；库里的默认值也是 0，能平滑兼容
            return 0;
        }
    }

    private record PermCache(Set<String> perms, long at) {
    }
}
