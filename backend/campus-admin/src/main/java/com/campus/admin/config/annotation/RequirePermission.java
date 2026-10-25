package com.campus.admin.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级权限点。
 *
 * <p>标在方法上优先，没标方法时退回到类上的注解。
 * value 为空表示「只要登录即可」。超级管理员（{@code super_flag=1}）一律放行。
 *
 * <p>这只是服务端拦截；前端用 {@code v-permission} 指令控制按钮显隐，
 * 两者必须成对出现——指令只是体验，本注解才是真正的边界。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    String[] value() default {};

    Logical logical() default Logical.OR;

    enum Logical {
        OR, AND
    }
}
