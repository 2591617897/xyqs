package com.campus.user.controller;

import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.AccountPurgeService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 后台管理专用内部接口（campus-admin → campus-user）。
 *
 * <p>三个动作写的都是 t_user 自己的列（status / mute_until / verify_status），
 * 归属方是 campus-user，后台不在自己库里留第二份，避免两边漂移。
 *
 * <p>mute_until 只是投影列：后台的 t_user_mute 才是事实源，
 * 这里不判断禁言是否真的生效，只负责把投影刷成一致。
 */
@Slf4j
@RestController
@RequestMapping("/internal/admin/users")
@RequiredArgsConstructor
public class InternalAdminUserController {

    private final UserMapper userMapper;
    private final AccountPurgeService accountPurgeService;

    /** 封禁 / 解封：status 0封禁 1正常 */
    @PostMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Integer status = intOf(body.get("status"));
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 非法，只允许 0封禁 / 1正常");
        }
        /*
         * 封禁 / 解封都必须让 token_version + 1。
         *
         * 只改 status 是不够的：JWT 无状态，对方手里那张 72 小时的令牌不会因为改库而失效，
         * 而内容服务只校验令牌、不查 status —— 这就是第三轮审计里
         * 「封禁后还能发三天内容」的直接原因（P0-1）。
         * 版本号 +1 后，网关在 60 秒内（缓存过期）就会把旧令牌判失效。
         *
         * 解封同样要 +1：否则封禁期间被网关拒掉的旧令牌会在解封瞬间「复活」，
         * 而这些令牌可能是封禁前就流出去的。让用户重新登录一次更干净。
         */
        int rows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getStatus, status)
                .setSql("token_version = token_version + 1")
                .eq(User::getId, id));
        if (rows == 0) {
            throw BizException.notFound("用户不存在");
        }
        log.info("[admin] 用户 {} -> status={}（令牌版本 +1），原因：{}", id, status, body.get("reason"));
        return R.ok();
    }

    /**
     * 禁言投影：muteUntil 为 null 表示解除。
     *
     * <p>这里必须用 Wrapper 显式 SET，不能用 updateById：MyBatis-Plus 默认的 NOT_NULL 字段策略
     * 会跳过 null 字段，解除禁言时 mute_until 就永远清不掉（用户依旧显示「被禁言」）。
     */
    @PostMapping("/{id}/mute")
    public R<Void> mute(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("muteUntil");
        LocalDateTime until = null;
        if (raw != null && !"".equals(String.valueOf(raw))) {
            until = LocalDateTime.parse(String.valueOf(raw));
        }
        if (userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getMuteUntil, until)
                .eq(User::getId, id)) == 0) {
            throw BizException.notFound("用户不存在");
        }
        log.info("[admin] 用户 {} 禁言投影 -> {}，原因：{}", id, until, body.get("reason"));
        return R.ok();
    }

    /** 实名审核结论：verifyStatus 2通过 3驳回 */
    @PostMapping("/{id}/verify")
    public R<Void> verify(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Integer verifyStatus = intOf(body.get("verifyStatus"));
        if (verifyStatus == null || (verifyStatus != 2 && verifyStatus != 3)) {
            throw BizException.of("verifyStatus 非法，只允许 2通过 / 3驳回");
        }
        if (verifyStatus == 3) {
            Object remark = body.get("reason");
            if (remark == null || String.valueOf(remark).isBlank()) {
                throw BizException.of("驳回必须填写原因");
            }
        }
        // 同 mute：审核通过时要把上次的驳回原因一并清掉，只能显式 SET null
        String remark = verifyStatus == 3 ? String.valueOf(body.get("reason")) : null;
        if (userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getVerifyStatus, verifyStatus)
                .set(User::getVerifyRemark, remark)
                .eq(User::getId, id)) == 0) {
            throw BizException.notFound("用户不存在");
        }
        log.info("[admin] 用户 {} 实名审核 -> {}，原因：{}", id, verifyStatus, body.get("reason"));
        return R.ok();
    }

    private Integer intOf(Object v) {
        return v == null ? null : Integer.valueOf(String.valueOf(v));
    }

    /**
     * 清理满保留期的注销账号（campus-admin 每日定时触发）。
     *
     * <p>保留期天数由 campus-user 自己配置（{@code campus.account.cancel-retain-days}），
     * 后台只传单批条数，避免两处天数不一致导致「后台以为到期了，用户服务还没到期」。
     */
    @PostMapping("/purge-cancelled")
    public R<Integer> purgeCancelled(@RequestBody(required = false) Map<String, Object> body) {
        int limit = 200;
        if (body != null && body.get("limit") != null) {
            limit = Math.min(Math.max(intOf(body.get("limit")), 1), 1000);
        }
        int n = accountPurgeService.purgeExpired(limit);
        if (n > 0) {
            log.info("[admin] 清理满保留期注销账号 {} 个", n);
        }
        return R.ok(n);
    }
}
