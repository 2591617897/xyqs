package com.campus.mail.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部发信接口入参。
 *
 * <p>只有 {@code template + params}，没有 {@code html} 字段 —— 正文一律由服务端模板生成，
 * 免得任何调用方都能借我们的域名发任意内容的邮件。
 */
@Data
public class MailSendReq {

    @NotBlank(message = "收件地址不能为空")
    @Size(max = 255, message = "收件地址过长")
    private String to;

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 48, message = "模板编码过长")
    private String template;

    private Map<String, String> params = new HashMap<>();

    @Size(max = 32, message = "业务类型过长")
    private String bizType;

    @Size(max = 64, message = "业务主键过长")
    private String bizId;

    /** 可选：调用方自带幂等键；不传则服务端生成 */
    @Size(max = 32, message = "幂等键过长")
    private String requestId;
}
