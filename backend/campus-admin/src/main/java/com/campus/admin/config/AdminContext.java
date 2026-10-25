package com.campus.admin.config;

import com.campus.common.exception.BizException;

import java.util.List;
import java.util.Set;

/**
 * 当前请求的后台管理员上下文。
 *
 * <p>由 {@link AdminAuthInterceptor} 从网关注入的 {@code X-Admin-*} 头解析后写入，
 * 线程内有效，请求结束必须 {@link #clear()} —— 写法对齐 common 的 {@code UserContext}。
 */
public final class AdminContext {

    public record AdminUser(Long adminId, String username, boolean superAdmin,
                            String scope, List<Long> regionIds, Set<String> permissions) {
    }

    private static final ThreadLocal<AdminUser> HOLDER = new ThreadLocal<>();

    private AdminContext() {
    }

    public static void set(AdminUser user) {
        HOLDER.set(user);
    }

    public static AdminUser get() {
        return HOLDER.get();
    }

    public static Long adminId() {
        AdminUser u = HOLDER.get();
        return u == null ? null : u.adminId();
    }

    public static String username() {
        AdminUser u = HOLDER.get();
        return u == null ? null : u.username();
    }

    public static boolean superAdmin() {
        AdminUser u = HOLDER.get();
        return u != null && u.superAdmin();
    }

    public static Long requireAdminId() {
        Long id = adminId();
        if (id == null) {
            throw BizException.unauthorized();
        }
        return id;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
