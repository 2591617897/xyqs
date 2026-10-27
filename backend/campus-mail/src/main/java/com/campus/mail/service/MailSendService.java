package com.campus.mail.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.mail.MailMessage;
import com.campus.mail.dto.RenderedMail;
import com.campus.mail.entity.MailLog;
import com.campus.mail.mapper.MailLogMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 真正的发信动作 + 发信日志。
 *
 * <p><b>失败必须抛出</b>：异常会让 Spring AMQP 按配置重试（默认 3 次、2 秒起、指数退避），
 * 重试耗尽后消息被 reject 进死信队列。如果这里把异常吞掉只打日志，
 * MQ 会认为消费成功，那封没发出去的信就彻底消失了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailSendService {

    private final JavaMailSender mailSender;
    private final MailLogMapper logMapper;
    private final MailTemplateRegistry templates;

    @Value("${campus.mail.from:}")
    private String configuredFrom;

    @Value("${spring.mail.username:}")
    private String defaultFrom;

    /**
     * 发送一封邮件（幂等）。
     *
     * @throws IllegalStateException SMTP 侧失败，调用方（消费者）应让异常冒泡以触发重试
     */
    public void send(MailMessage msg) {
        if (msg == null) {
            throw new IllegalArgumentException("邮件消息为空");
        }
        if (!StringUtils.hasText(msg.getRequestId())) {
            msg.setRequestId(IdWorker.getIdStr());
        }
        String requestId = msg.getRequestId();

        MailLog exist = logMapper.selectOne(Wrappers.<MailLog>lambdaQuery()
                .eq(MailLog::getRequestId, requestId));
        if (exist != null && Integer.valueOf(MailLog.STATUS_SENT).equals(exist.getStatus())) {
            // MQ 是「至少一次」投递，重复到达很常见；已经成功过的直接跳过
            log.info("[mail] 重复消息已跳过 requestId={}", requestId);
            return;
        }
        if (exist == null) {
            MailLog row = new MailLog();
            row.setRequestId(requestId);
            row.setBizType(msg.getBizType());
            row.setBizId(msg.getBizId());
            row.setRecipient(msg.getTo() == null ? "" : msg.getTo());
            row.setTemplate(msg.getTemplate());
            row.setStatus(MailLog.STATUS_PENDING);
            row.setRetryCount(0);
            logMapper.insert(row);
        }

        try {
            if (!StringUtils.hasText(msg.getTo())) {
                throw new IllegalArgumentException("收件地址为空");
            }
            RenderedMail mail = templates.render(msg);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromAddress());
            helper.setTo(splitRecipients(msg.getTo()));
            helper.setSubject(mail.subject());
            // 同时给纯文本与 HTML（multipart/alternative）
            helper.setText(mail.plainText(), mail.html());
            mailSender.send(mime);

            logMapper.update(null, Wrappers.<MailLog>lambdaUpdate()
                    .eq(MailLog::getRequestId, requestId)
                    .set(MailLog::getStatus, MailLog.STATUS_SENT)
                    .set(MailLog::getSubject, mail.subject())
                    .set(MailLog::getSentAt, LocalDateTime.now())
                    .set(MailLog::getErrorMsg, null)
                    .setSql("retry_count = retry_count + 1"));
            log.info("[mail] 发送成功 requestId={} to={} template={}",
                    requestId, mask(msg.getTo()), msg.getTemplate());
        } catch (Exception e) {
            logMapper.update(null, Wrappers.<MailLog>lambdaUpdate()
                    .eq(MailLog::getRequestId, requestId)
                    .set(MailLog::getStatus, MailLog.STATUS_FAILED)
                    .set(MailLog::getErrorMsg, trunc(e.getMessage(), 500))
                    .setSql("retry_count = retry_count + 1"));
            log.error("[mail] 发送失败 requestId={} to={} template={} 原因={}",
                    requestId, mask(msg.getTo()), msg.getTemplate(), e.getMessage());
            throw new IllegalStateException("邮件发送失败：" + e.getMessage(), e);
        }
    }

    /** 按 requestId 查发送结果，供调用方确认「到底发出去没有」 */
    public MailLog findByRequestId(String requestId) {
        return logMapper.selectOne(Wrappers.<MailLog>lambdaQuery()
                .eq(MailLog::getRequestId, requestId));
    }

    private String fromAddress() {
        String from = StringUtils.hasText(configuredFrom) ? configuredFrom.trim() : defaultFrom;
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException("未配置发件人地址（campus.mail.from / spring.mail.username）");
        }
        return from;
    }

    private String[] splitRecipients(String to) {
        String[] arr = Arrays.stream(to.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        if (arr.length == 0) {
            throw new IllegalArgumentException("收件地址为空");
        }
        return arr;
    }

    private String trunc(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 日志里不写完整邮箱 */
    private String mask(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
