package com.campus.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 客户端真实 IP 的唯一解析入口。
 *
 * <p><b>为什么必须统一到一个地方</b>：登录限流、后台找回密码限流、审计日志、广告去重
 * 全都依赖「这个请求来自哪个 IP」。以前这四个地方各写一份 {@code clientIp()}，
 * 而且都取 {@code X-Forwarded-For} 的<b>第一段</b>——这是个可被完全绕过的漏洞：
 *
 * <pre>
 * Nginx 用 $proxy_add_x_forwarded_for 的含义是「在客户端自带的 XFF 后面追加真实 IP」：
 *   客户端发：X-Forwarded-For: 1.2.3.4        （攻击者自己填的）
 *   Nginx 转出：X-Forwarded-For: 1.2.3.4, 真实IP
 *   后端取 split(",")[0] → 1.2.3.4            ← 攻击者的值
 * </pre>
 *
 * <p>攻击者每次请求换一个伪造值，限流的 {@code ip:xxx} 桶永远是新桶，
 * 「单 IP 每分钟 30 次登录」「单 IP 每小时 20 次发码」全部形同虚设。
 *
 * <p><b>现在的取值顺序</b>：
 * <ol>
 *   <li><b>{@code X-Real-IP}</b> —— 唯一可信来源。Nginx 用
 *       {@code proxy_set_header X-Real-IP $remote_addr} <b>覆盖</b>写入（不是追加），
 *       客户端自带的同名头会被直接丢弃；Spring Cloud Gateway 也不改写这个头，
 *       所以它从 Nginx 到业务服务一路不会被污染。</li>
 *   <li>{@code X-Forwarded-For} 第一段 —— 兜底。仅当部署没配 {@code X-Real-IP} 时使用，
 *       且 {@code deploy/nginx-*.conf} 已改为 {@code $remote_addr} 覆盖式写入，
 *       所以此时第一段也是真实 IP（不再是被追加在前的伪造值）。</li>
 *   <li>{@code request.getRemoteAddr()} —— 本机直连（开发环境）时的最后兜底。</li>
 * </ol>
 *
 * <p><b>注意</b>：如果将来在前面再加 CDN，必须改用 Nginx 的
 * {@code real_ip_header X-Forwarded-For} + {@code set_real_ip_from CDN 网段}，
 * 让 {@code $remote_addr} 先被还原成真实客户端 IP，本类无需改动。
 */
public final class ClientIp {

    /**
     * 宽松的 IP 形状校验：只要不像 IP 就忽略，避免把
     * {@code unknown} / 空串 / 攻击者塞的长串当成限流 key（否则等于送他无限新桶）。
     * 不做严格的 IPv6 位级校验——这里只需要「同一 IP 稳定落到同一个 key」。
     */
    private static final Pattern IP_LIKE = Pattern.compile("^[0-9a-fA-F:.]{3,45}$");

    private static final String UNKNOWN = "unknown";

    private ClientIp() {
    }

    /**
     * 解析客户端 IP，永不返回 null（拿不到时返回 {@code "unknown"}）。
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        // ① X-Real-IP：Nginx 覆盖式写入，唯一可信
        String real = request.getHeader("X-Real-IP");
        if (isIpLike(real)) {
            return real.trim();
        }
        // ② XFF 第一段：Nginx 已改为覆盖式（$remote_addr），第一段即真实来源
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            for (String seg : xff.split(",")) {
                String candidate = seg.trim();
                if (isIpLike(candidate)) {
                    return candidate;
                }
                // 只有第一段有参考价值；第一段不合法说明是伪造或代理异常，直接放弃这个头
                break;
            }
        }
        // ③ 本机直连
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote.trim() : UNKNOWN;
    }

    private static boolean isIpLike(String v) {
        return StringUtils.hasText(v)
                && !UNKNOWN.equalsIgnoreCase(v.trim())
                && IP_LIKE.matcher(v.trim()).matches();
    }
}
