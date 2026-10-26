package com.campus.admin.job;

import com.campus.admin.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 公告定时下线。
 *
 * <p>为什么需要它：C 端判断公告是否可见只看 {@code offline_at}，所以运营填了
 * 「自动下线时间」的那一刻，用户端就已经按点消失了——这个任务不负责「让公告下架」，
 * 只负责把后台的 <b>status</b> 翻成「已下线」，否则运营看到一条早已过期的公告
 * 还挂着「已发布」，会以为配置没生效，反复点下线。
 *
 * <p>延迟 1 分钟是因为启动阶段数据库连接池还在预热，没必要抢那几秒；
 * 之后每 5 分钟扫一次，最坏情况下后台状态比实际晚 5 分钟，用户端不受影响。
 * 单次 UPDATE 是幂等的（带 status=1 条件），多实例同时跑也不会重复处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeExpireJob {

    private final NoticeService noticeService;

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void expireNotices() {
        try {
            int n = noticeService.expireDue();
            if (n > 0) {
                log.info("[notice] 定时下线 {} 条到期公告", n);
            }
        } catch (Exception e) {
            // 定时任务不能抛：一次失败只影响这一轮，下一轮还会再扫
            log.warn("[notice] 定时下线失败，等下一轮重试：{}", e.getMessage());
        }
    }
}
