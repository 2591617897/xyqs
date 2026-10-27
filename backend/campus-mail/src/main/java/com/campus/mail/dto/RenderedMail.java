package com.campus.mail.dto;

/**
 * 渲染结果。纯文本与 HTML 都要有：只发 HTML 的邮件在
 * 「拒绝 HTML 的客户端」和垃圾邮件评分里都吃亏。
 */
public record RenderedMail(String subject, String plainText, String html) {
}
