package com.campus.goods.task;

import com.campus.goods.service.HotWordsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 搜索日志清理：只保留最近 {@link HotWordsService#KEEP_DAYS} 天。
 *
 * <p>热词只看近 7 天，留着 90 天是为了偶尔回看「上个月大家在搜什么」；
 * 再往前的数据既没人查也白占索引。用 {@code DELETE ... WHERE search_day < ?} 分批删，
 * 有 {@code idx_day_result} 兜着，删起来不会全表扫。
 *
 * <p>6 小时跑一次而不是每天一次：服务器重启、任务错过窗口都能自动补上，
 * 而且每次删的行数很少，不会在业务高峰憋出长事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchLogCleanTask {

    private final HotWordsService hotWordsService;

    @Scheduled(initialDelay = 10 * 60_000L, fixedDelay = 6 * 60 * 60_000L)
    public void run() {
        try {
            int removed = hotWordsService.cleanExpired();
            if (removed > 0) {
                log.info("[hot-words] 清理 {} 天前的搜索日志 {} 条", HotWordsService.KEEP_DAYS, removed);
            }
        } catch (Exception e) {
            log.warn("[hot-words] 搜索日志清理异常: {}", e.getMessage());
        }
    }
}
