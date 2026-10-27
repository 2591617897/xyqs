package com.campus.mail.service;

import com.campus.common.mail.MailMessage;
import com.campus.common.mail.MailTemplates;
import com.campus.mail.dto.RenderedMail;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 邮件模板注册表。
 *
 * <p><b>正文只在这里生成</b>。业务方传 {@code template + params} 就行，
 * 不传 HTML —— 否则改一次版式要把所有发信的服务挨个发版，最后必然漂移成好几种样式。
 *
 * <p>新增一种邮件：加常量 + 加一个 case + 在下面的写法里补齐参数校验。
 */
@Component
public class MailTemplateRegistry {

    /** 后台找回密码验证码（编码定义在 common 的 MailTemplates，投递方与消费方共用） */
    public static final String ADMIN_RESET_CODE = MailTemplates.ADMIN_RESET_CODE;

    /** 通用兜底：params.subject + params.content，仅建议在临时/内部场景使用 */
    public static final String GENERIC = MailTemplates.GENERIC;

    public RenderedMail render(MailMessage msg) {
        Map<String, String> p = msg.getParams() == null ? Map.of() : msg.getParams();
        String code = msg.getTemplate() == null ? "" : msg.getTemplate().trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case ADMIN_RESET_CODE -> adminResetCode(p);
            case GENERIC -> generic(p);
            default -> throw new IllegalArgumentException("未知邮件模板：" + msg.getTemplate());
        };
    }

    private RenderedMail adminResetCode(Map<String, String> p) {
        String username = require(p, "username");
        String code = require(p, "code");
        int minutes = parseInt(p.get("expireMinutes"), 10);
        String ip = p.get("ip");

        String ipLine = StringUtils.hasText(ip)
                ? "<p style=\"margin:6px 0 0;color:#8a8f99;font-size:13px;\">申请来源 IP：" + escape(ip) + "</p>"
                : "";
        String plain = """
                校聚一城 · 管理后台密码重置

                账号：%s
                验证码：%s
                有效期：%d 分钟

                如果不是您本人操作，请忽略本邮件，并联系超级管理员检查该账号。
                %s""".formatted(username, code, minutes,
                StringUtils.hasText(ip) ? "申请来源 IP：" + ip : "");

        // 邮件客户端对 style 标签支持极差，一律内联样式；不用外链图片（默认被拦截后变成破图）
        String html = """
                <div style="background:#f5f6f8;padding:24px 12px;font-family:-apple-system,'PingFang SC',Arial,sans-serif;">
                  <div style="max-width:520px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;
                              border:1px solid #e8eaee;">
                    <div style="background:#2f6fed;padding:20px 24px;color:#fff;">
                      <div style="font-size:16px;font-weight:600;">校聚一城 · 管理后台</div>
                      <div style="font-size:13px;opacity:.85;margin-top:4px;">密码重置验证码</div>
                    </div>
                    <div style="padding:24px;">
                      <p style="margin:0 0 16px;color:#303133;font-size:14px;">您好，<b>%s</b>：</p>
                      <p style="margin:0 0 16px;color:#606266;font-size:14px;line-height:1.7;">
                        您正在重置管理后台登录密码，请在页面中填入以下验证码：
                      </p>
                      <div style="text-align:center;margin:22px 0;">
                        <span style="display:inline-block;padding:12px 28px;background:#eef3ff;color:#2f6fed;
                                     font-size:28px;font-weight:700;letter-spacing:8px;border-radius:8px;">%s</span>
                      </div>
                      <p style="margin:0;color:#606266;font-size:13px;">验证码 %d 分钟内有效，仅可使用一次。</p>
                      %s
                      <div style="margin-top:20px;padding-top:16px;border-top:1px solid #f0f1f4;
                                  color:#909399;font-size:12px;line-height:1.7;">
                        如果这不是您本人的操作，请忽略本邮件，并尽快联系超级管理员检查账号安全。<br/>
                        本邮件由系统自动发送，请勿直接回复。
                      </div>
                    </div>
                  </div>
                </div>
                """.formatted(escape(username), escape(code), minutes, ipLine);

        return new RenderedMail("【校聚一城】管理后台密码重置验证码", plain, html);
    }

    private RenderedMail generic(Map<String, String> p) {
        String subject = require(p, "subject");
        String content = require(p, "content");
        String html = """
                <div style="background:#f5f6f8;padding:24px 12px;font-family:-apple-system,'PingFang SC',Arial,sans-serif;">
                  <div style="max-width:520px;margin:0 auto;background:#fff;border-radius:12px;padding:24px;
                              border:1px solid #e8eaee;color:#303133;font-size:14px;line-height:1.8;">
                    %s
                    <div style="margin-top:20px;padding-top:16px;border-top:1px solid #f0f1f4;
                                color:#909399;font-size:12px;">本邮件由系统自动发送，请勿直接回复。</div>
                  </div>
                </div>
                """.formatted(escape(content).replace("\n", "<br/>"));
        return new RenderedMail(subject, content, html);
    }

    private String require(Map<String, String> p, String key) {
        String v = p.get(key);
        if (!StringUtils.hasText(v)) {
            throw new IllegalArgumentException("邮件模板缺少参数：" + key);
        }
        return v.trim();
    }

    private int parseInt(String v, int fallback) {
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 参数里可能带用户输入（账号名、IP），进 HTML 前统一转义 */
    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
