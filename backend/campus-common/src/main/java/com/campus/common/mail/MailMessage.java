package com.campus.common.mail;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 邮件投递消息（MQ 载体）。
 *
 * <p><b>只传「模板 + 参数」，不传渲染好的 HTML</b>：
 * 正文排版、品牌样式、合规落款都归 campus-mail 一处维护；
 * 各业务方各自拼 HTML 的话，改一次模板就得挨个服务发版，最后一定会漂移成五六种版式。
 *
 * <p>新增一种邮件 = 在 campus-mail 的 {@code MailTemplateRegistry} 注册一个模板编码，
 * 业务方传 {@code template} + {@code params} 即可，消息结构本身不用动。
 */
@Data
public class MailMessage implements Serializable {

    /** 幂等键：同一条 requestId 只会真正发出一封（消费端按 t_mail_log 去重） */
    private String requestId;

    /** 收件地址；多个用英文逗号分隔 */
    private String to;

    /** 模板编码，见 MailTemplateRegistry#TEMPLATES */
    private String template;

    /** 模板参数，键名由模板自己约定 */
    private Map<String, String> params = new HashMap<>();

    /** 业务追踪：由哪个服务、因为什么事发的（写进发信日志，排障时能倒查） */
    private String bizType;
    private String bizId;

    public MailMessage put(String key, String value) {
        if (params == null) {
            params = new HashMap<>();
        }
        params.put(key, value);
        return this;
    }
}
