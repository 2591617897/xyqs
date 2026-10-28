package com.campus.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.admin.config.AdminAuthInterceptor;
import com.campus.admin.config.AdminPasswordUtil;
import com.campus.admin.dto.ResetPasswordByEmailReq;
import com.campus.admin.dto.SendResetCodeReq;
import com.campus.admin.dto.SendResetCodeResp;
import com.campus.admin.entity.AdminPwdReset;
import com.campus.admin.entity.AdminUser;
import com.campus.admin.mapper.AdminPwdResetMapper;
import com.campus.admin.mapper.AdminUserMapper;
import com.campus.common.exception.BizException;
import com.campus.common.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 后台「邮箱找回密码」。
 *
 * <p><b>这是全站唯一一个「未登录就能改别人密码」的入口</b>，所以规则写得比别处更硬：
 * <ol>
 *   <li>收件邮箱只从库里的 {@code t_admin_user.email} 取，<b>前端不能指定</b>——
 *       否则接口就成了「任意邮箱收码 + 任意账号改密」</li>
 *   <li>验证码只存 BCrypt 哈希，6 位数字空间虽小，但一次校验就是一次 BCrypt 计算，
 *       库被读走也没法离线穷举出可直接使用的结果</li>
 *   <li>三重限流：60 秒冷却、单账号 24h 5 次、单 IP 1h 20 次（含账号不存在的情况）</li>
 *   <li>校验失败计数到 5 次立即作废，不给「慢慢试」的机会</li>
 *   <li>重置成功顺带清掉登录失败计数和锁定时间——被锁 15 分钟的人正是最需要这条通道的人</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminUserMapper adminUserMapper;
    private final AdminPwdResetMapper resetMapper;
    private final AdminMailPublisher mailPublisher;

    @Value("${campus.admin.reset-pwd.code-minutes:10}")
    private int codeMinutes;

    @Value("${campus.admin.reset-pwd.max-attempt:5}")
    private int maxAttempt;

    @Value("${campus.admin.reset-pwd.resend-seconds:60}")
    private int resendSeconds;

    @Value("${campus.admin.reset-pwd.daily-limit:5}")
    private int dailyLimit;

    @Value("${campus.admin.reset-pwd.ip-hourly-limit:20}")
    private int ipHourlyLimit;

    /**
     * 发送验证码。
     *
     * <p>账号不存在 / 未绑邮箱都给明确提示而不是「已发送」——
     * 这是内部后台，运营排障的价值高于抗枚举，枚举风险由 IP 限流兜住。
     */
    public SendResetCodeResp sendCode(SendResetCodeReq req) {
        String username = req.getUsername().trim();
        String ip = clientIp();
        LocalDateTime now = LocalDateTime.now();

        // IP 限流放在最前面：账号存在与否都要先过这一关，否则就成了免费的账号探测器
        Long ipUsed = resetMapper.selectCount(Wrappers.<AdminPwdReset>lambdaQuery()
                .eq(AdminPwdReset::getReqIp, ip)
                .gt(AdminPwdReset::getCreatedAt, now.minusHours(1)));
        if (ipUsed != null && ipUsed >= ipHourlyLimit) {
            log.warn("[security] 找回密码发码被 IP 限流 ip={} count={}", ip, ipUsed);
            throw BizException.of("操作过于频繁，请 1 小时后再试");
        }

        AdminUser user = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, username));
        if (user == null) {
            throw BizException.notFound("账号不存在，请核对后重试");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.forbidden("账号已禁用，请联系超级管理员");
        }
        if (!StringUtils.hasText(user.getEmail())) {
            throw BizException.of("该账号未绑定邮箱，请联系超级管理员重置密码");
        }
        String email = user.getEmail().trim();

        Long recent = resetMapper.selectCount(Wrappers.<AdminPwdReset>lambdaQuery()
                .eq(AdminPwdReset::getAdminId, user.getId())
                .gt(AdminPwdReset::getCreatedAt, now.minusSeconds(resendSeconds)));
        if (recent != null && recent > 0) {
            throw BizException.of("验证码已发出，请 " + resendSeconds + " 秒后再试");
        }
        Long daily = resetMapper.selectCount(Wrappers.<AdminPwdReset>lambdaQuery()
                .eq(AdminPwdReset::getAdminId, user.getId())
                .gt(AdminPwdReset::getCreatedAt, now.minusHours(24)));
        if (daily != null && daily >= dailyLimit) {
            throw BizException.of("该账号 24 小时内发送次数已达上限，请联系超级管理员");
        }

        // 新码生效前先作废旧的，避免同时存在两个可用验证码
        resetMapper.update(null, Wrappers.<AdminPwdReset>lambdaUpdate()
                .eq(AdminPwdReset::getAdminId, user.getId())
                .eq(AdminPwdReset::getUsed, 0)
                .set(AdminPwdReset::getUsed, 1));

        String code = randomCode();
        AdminPwdReset row = new AdminPwdReset();
        row.setAdminId(user.getId());
        row.setEmail(email);
        row.setCodeHash(AdminPasswordUtil.hash(code));
        row.setExpireAt(now.plusMinutes(codeMinutes));
        row.setUsed(0);
        row.setAttemptCount(0);
        row.setReqIp(ip);
        resetMapper.insert(row);

        try {
            // 只投递到 MQ，不在这里等 SMTP。邮件服务是独立进程，
            // 它挂了消息会在队列里等它恢复；broker 本身不可用才会抛异常。
            mailPublisher.publishResetCode(email, username, code, codeMinutes, ip, user.getId());
        } catch (Exception e) {
            // 投不出去就把这条记录撤掉：留着会让用户被 60 秒冷却挡在门外，
            // 明明一封邮件都没收到却要干等一分钟。
            resetMapper.deleteById(row.getId());
            throw BizException.of("验证码邮件发送失败，请稍后重试或联系超级管理员");
        }
        return new SendResetCodeResp(mask(email), codeMinutes, resendSeconds);
    }

    /**
     * 用验证码重置密码。
     *
     * <p>成功后会：写入新密码、清失败计数与锁定、token_version + 1（所有旧令牌立即失效）、
     * 清权限缓存、作废该账号全部未用验证码。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordByEmailReq req) {
        String username = req.getUsername().trim();
        AdminUser user = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, username));
        if (user == null) {
            throw BizException.of("账号不存在，请核对后重试");
        }

        AdminPwdReset row = resetMapper.selectOne(Wrappers.<AdminPwdReset>lambdaQuery()
                .eq(AdminPwdReset::getAdminId, user.getId())
                .eq(AdminPwdReset::getUsed, 0)
                .orderByDesc(AdminPwdReset::getCreatedAt)
                .last("limit 1"));
        if (row == null) {
            throw BizException.of("请先获取邮箱验证码");
        }
        LocalDateTime now = LocalDateTime.now();
        if (row.getExpireAt() == null || row.getExpireAt().isBefore(now)) {
            invalidate(row.getId());
            throw BizException.of("验证码已过期，请重新获取");
        }
        int attempts = row.getAttemptCount() == null ? 0 : row.getAttemptCount();
        if (attempts >= maxAttempt) {
            invalidate(row.getId());
            throw BizException.of("验证码错误次数过多，请重新获取");
        }
        if (!AdminPasswordUtil.matches(req.getCode(), row.getCodeHash(), null)) {
            resetMapper.update(null, Wrappers.<AdminPwdReset>lambdaUpdate()
                    .eq(AdminPwdReset::getId, row.getId())
                    .set(AdminPwdReset::getAttemptCount, attempts + 1));
            int left = maxAttempt - attempts - 1;
            if (left <= 0) {
                invalidate(row.getId());
                throw BizException.of("验证码错误次数过多，请重新获取");
            }
            throw BizException.of("验证码不正确，还可尝试 " + left + " 次");
        }

        String weak = AdminPasswordUtil.checkStrength(req.getNewPassword(), user.getUsername());
        if (weak != null) {
            // 验证码正确但密码不合格时不消耗验证码，让用户直接改密码重试
            throw BizException.of(weak);
        }
        if (AdminPasswordUtil.matches(req.getNewPassword(), user.getPassword(), user.getSalt())) {
            throw BizException.of("新密码不能与原密码相同");
        }

        // 一次 update 写全：updateById 会跳过 null，locked_until 清不掉。
        // 顺带把失败计数和锁定一起清掉——刚被锁 15 分钟的人正是最需要这条通道的人。
        adminUserMapper.update(null, Wrappers.<AdminUser>lambdaUpdate()
                .eq(AdminUser::getId, user.getId())
                .set(AdminUser::getPassword, AdminPasswordUtil.hash(req.getNewPassword()))
                // BCrypt 自带随机盐，salt 列留空只为兼容历史记录
                .set(AdminUser::getSalt, "")
                .set(AdminUser::getMustChangePwd, 0)
                .set(AdminUser::getFailCount, 0)
                .set(AdminUser::getLockedUntil, null));

        adminUserMapper.bumpTokenVersion(user.getId());
        AdminAuthInterceptor.evictPermissionCache(user.getId());
        invalidate(row.getId());
        log.info("[security] 管理员 {} 通过邮箱验证码重置了密码，旧令牌已全部失效", user.getUsername());
    }

    private void invalidate(Long id) {
        resetMapper.update(null, Wrappers.<AdminPwdReset>lambdaUpdate()
                .eq(AdminPwdReset::getId, id)
                .set(AdminPwdReset::getUsed, 1));
    }

    /** 邮箱脱敏：只留首尾字符和域名，完整地址不进前端（页面截图、浏览器历史都算泄漏面） */
    static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String name = email.substring(0, at);
        String domain = email.substring(at);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }

    /** 6 位数字，带前导零；用 SecureRandom 而不是 Random */
    private String randomCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /**
     * 客户端 IP，用作「单 IP 每小时 20 次发码」的限流维度。
     *
     * <p>必须走 {@code ClientIp}（优先 X-Real-IP）：以前取 XFF 第一段，
     * 而 Nginx 是追加式写入，攻击者自带一个伪造值就能每次换桶，
     * 发码接口会变成任人使用的邮件轰炸机。
     */
    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return ClientIp.resolve(attrs.getRequest());
        }
        return "unknown";
    }
}
