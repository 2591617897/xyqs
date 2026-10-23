package com.campus.common.security;

import com.campus.common.exception.BizException;

/**
 * 当前请求的登录用户上下文（由网关透传的 Header 注入）。
 * 线程内有效，请求结束由拦截器清理。
 */
public final class UserContext {

    public record LoginUser(Long userId, Long schoolId, Integer verifyStatus) {
    }

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.userId();
    }

    public static Long schoolId() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.schoolId();
    }

    public static Integer verifyStatus() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.verifyStatus();
    }

    public static Long requireUserId() {
        Long id = userId();
        if (id == null) {
            throw BizException.unauthorized();
        }
        return id;
    }

    /**
     * 发布类操作必须已完成校园身份认证。
     */
    public static Long requireVerifiedUserId() {
        Long id = requireUserId();
        Integer vs = verifyStatus();
        if (vs == null || vs != 2) {
            throw BizException.forbidden("请先完成校园身份认证后再发布内容");
        }
        return id;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
