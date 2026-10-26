package com.campus.promote.task;

import com.campus.promote.service.PromoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 推广分衰减：把仍有推广记录的内容重新算一遍有效分并回写内容表。
 *
 * <p>周期 30 分钟。分数是按「剩余时间线性折算」的连续值，
 * 不重算的话内容表上会一直挂着推广当天的满分，排序就不随时间回落了。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromoteDecayTask {

    private final PromoteService promoteService;

    @Scheduled(initialDelay = 60_000L, fixedDelay = 30 * 60_000L)
    public void run() {
        try {
            int n = promoteService.refreshExpired();
            if (n > 0) {
                log.info("[promote] 衰减重算完成，回写 {} 条内容", n);
            }
        } catch (Exception e) {
            log.warn("[promote] 衰减重算异常: {}", e.getMessage());
        }
    }
}
