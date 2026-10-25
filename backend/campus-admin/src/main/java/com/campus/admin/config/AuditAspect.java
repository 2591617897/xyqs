package com.campus.admin.config;

import com.campus.admin.config.annotation.AuditLog;
import com.campus.admin.entity.AdminOperationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 敏感操作审计切面。
 *
 * <p>只做三件事：拼日志对象、脱敏、入队。<strong>切面里没有任何 IO</strong>，
 * 落库由 {@link AsyncAuditWriter} 的守护线程完成。
 *
 * <p>只读列表默认不标 {@link AuditLog}；唯一例外是「查看聊天记录」——
 * 那是对用户隐私的读取，事后要能追到谁看过。
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private static final Set<String> SENSITIVE_KEYS =
            Set.of("password", "oldpassword", "newpassword", "token", "secret");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private AsyncAuditWriter writer;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long t0 = System.currentTimeMillis();
        AdminContext.AdminUser u = AdminContext.get();
        HttpServletRequest request = currentRequest();
        String params = auditLog.saveParams() ? safeParams(pjp.getArgs()) : null;

        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            try {
                writer.offer(build(auditLog, u, request, params, error, System.currentTimeMillis() - t0, pjp));
            } catch (Exception e) {
                // 日志失败绝不能影响业务
                log.warn("组装审计日志失败 {}", e.getMessage());
            }
        }
    }

    private AdminOperationLog build(AuditLog auditLog, AdminContext.AdminUser u, HttpServletRequest request,
                                    String params, Throwable error, long cost, ProceedingJoinPoint pjp) {
        AdminOperationLog record = new AdminOperationLog();
        record.setAdminId(u == null ? null : u.adminId());
        // 登录接口执行时还没有登录态（AdminContext 为空），从入参里把账号名捞出来，
        // 否则「谁在撞密码」这条最该留痕的日志反而查不到人
        record.setUsername(u == null ? usernameFromArgs(pjp.getArgs()) : u.username());
        record.setModule(auditLog.module());
        record.setOperation(auditLog.value());
        record.setMethod(pjp.getSignature().getDeclaringTypeName() + "#" + pjp.getSignature().getName());
        record.setResult(error == null ? 0 : 1);
        record.setErrorMsg(error == null ? null : trunc(error.getMessage(), 500));
        record.setCostMs((int) cost);
        record.setParams(params);
        record.setCreatedAt(LocalDateTime.now());
        if (request != null) {
            record.setUri(request.getRequestURI());
            record.setHttpMethod(request.getMethod());
            record.setIp(firstIp(request.getHeader("X-Forwarded-For"), request.getRemoteAddr()));
            record.setUa(trunc(request.getHeader("User-Agent"), 500));
        }
        return record;
    }

    private String safeParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object a = args[i];
            if (a instanceof MultipartFile file) {
                sb.append("{\"file\":\"").append(file.getOriginalFilename())
                        .append("\",\"size\":").append(file.getSize()).append('}');
                continue;
            }
            if (a instanceof HttpServletRequest) {
                sb.append("{\"request\":true}");
                continue;
            }
            try {
                String json = MAPPER.writeValueAsString(a);
                for (String key : SENSITIVE_KEYS) {
                    json = json.replaceAll("(?i)\"" + key + "\"\\s*:\\s*\"[^\"]*\"",
                            "\"" + key + "\":\"***\"");
                }
                sb.append(json);
            } catch (Exception e) {
                sb.append(String.valueOf(a));
            }
        }
        sb.append(']');
        return trunc(sb.toString(), 2000);
    }

    private String usernameFromArgs(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object a : args) {
            if (a == null || a instanceof HttpServletRequest) {
                continue;
            }
            try {
                Map<?, ?> map = MAPPER.convertValue(a, Map.class);
                Object v = map.get("username");
                if (v != null) {
                    return String.valueOf(v);
                }
            } catch (Exception ignored) {
                // 不是 JSON 对象就跳过，不影响主流程
            }
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String firstIp(String xff, String fallback) {
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return fallback;
    }

    private String trunc(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }
}
