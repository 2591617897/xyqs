package com.campus.admin.job;

import com.campus.admin.client.UserAdminClient;
import com.campus.common.api.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 注销账号的保留期到期清理。
 *
 * <p>注销不是立即删除：账号先进入注销中状态，保留期（默认 365 天）内重新登录可恢复。
 * 满期后由本任务触发 campus-user 清除个人信息（昵称、头像、简介、手机号、学号、认证材料），
 * 并把 openid 换成占位值——之后再登录就是一个全新的空账号。
 *
 * <p>为什么任务放在 admin 而不是 campus-user：campus-user 只有 C 端接口，
 * 开 {@code @EnableScheduling} 会让每个实例都跑定时任务；后台本来就承载运营侧的
 * 周期性任务（公告下线等），多实例也少一些。真正的清理逻辑仍在 campus-user，
 * 这里只按天触发，避免两处各写一套删除规则。
 *
 * <p>每天凌晨 3:10 跑一次：比 0 点错开，避开日终统计；单批 500 条，
 * 清理是幂等的（{@code purged_at IS NULL} 作为条件），多实例或重跑都不会出错。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountPurgeJob {

    private static final int BATCH = 500;

    private final UserAdminClient userAdminClient;

    @Scheduled(cron = "0 10 3 * * ?")
    public void purgeExpiredAccounts() {
        try {
            R<Integer> res = userAdminClient.purgeCancelled(Map.of("limit", BATCH));
            Integer n = res == null ? null : res.getData();
            if (n != null && n > 0) {
                log.info("[account] 定时清理满保留期注销账号 {} 个", n);
            }
        } catch (Exception e) {
            // 定时任务不能抛：这一轮失败只影响本次，明天还会再扫
            log.warn("[account] 清理注销账号失败，等下一轮重试：{}", e.getMessage());
        }
    }
}
