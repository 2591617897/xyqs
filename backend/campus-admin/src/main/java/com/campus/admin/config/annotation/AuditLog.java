package com.campus.admin.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感操作审计。
 *
 * <p>切面只做「组装日志对象 + 入队」，不写库，由 {@code AsyncAuditWriter}
 * 的守护线程批量落库——任何情况下都不会拖慢业务请求。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 操作描述，如「封禁用户」 */
    String value();

    /** 业务模块 AUTH/CONTENT/USER/AD/REPORT/SYSTEM/NOTICE/CHAT/SENSITIVE */
    String module() default "";

    /** 是否记录请求参数（含密码的操作传 false） */
    boolean saveParams() default true;
}
