package com.campus.message.task;

import com.campus.message.service.GroupArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 群消息自动归档任务：每天把 12 个月前的消息挪进归档表。
 *
 * <p>⚠️ <b>必须确认 {@code MessageApplication} 上有 {@code @EnableScheduling}</b>。
 * 少了这个注解，{@code @Scheduled} <b>不生效而且不报错</b> ——
 * 项目里 {@code GoodsApplication} 的注释已经记过这个坑，这里再踩一次就太亏了。
 *
 * <p>用 {@code fixedDelay} 而不是 {@code cron}：服务器重启错过窗口能自动补上，
 * 不依赖精确时间点。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupMessageArchiveTask {

    private final GroupArchiveService archiveService;

    /** 启动 5 分钟后跑第一次（避开启动高峰），之后每 24 小时一次 */
    @Scheduled(initialDelay = 5 * 60_000L, fixedDelay = 24 * 3600_000L)
    public void run() {
        long start = System.currentTimeMillis();
        int total = 0;
        int batches = 0;
        try {
            while (batches < GroupArchiveService.maxBatchesPerRun()) {
                int moved = archiveService.moveBatch(500);
                if (moved == 0) {
                    break;
                }
                total += moved;
                batches++;
                // 让主库喘口气：一次性迁百万行会锁表 + 撑爆 undo log + 主从延迟飙升
                Thread.sleep(200);
            }
            if (total > 0) {
                log.info("[group-archive] 归档 {} 条 12 个月前的群消息，耗时 {} ms",
                        total, System.currentTimeMillis() - start);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 归档失败不能影响业务，下次再跑
            log.warn("[group-archive] 归档异常（已迁移 {} 条）：{}", total, e.getMessage());
        }
    }
}
