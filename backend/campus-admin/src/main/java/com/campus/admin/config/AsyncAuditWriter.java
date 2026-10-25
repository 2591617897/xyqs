package com.campus.admin.config;

import com.campus.admin.entity.AdminOperationLog;
import com.campus.admin.service.OperationLogService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 审计日志异步落库。
 *
 * <p>刻意不用 {@code @Async}：一是同类内部调用不生效，二是它走代理后容易和外部事务缠在一起。
 * 这里是「有界队列 + 单个守护线程批量写」：
 * <ul>
 *   <li>入队用非阻塞 {@code offer()}，队列满直接丢弃并计数——业务请求绝不因写日志而变慢；</li>
 *   <li>队列也就的保护了数据库抖动：一堆日志堆积在内存里比拖垮连接池好；</li>
 *   <li>审计允许极端情况下丢（每 100 条打一次日志），但不允许把主流程拖死。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncAuditWriter {

    private static final int QUEUE_CAP = 4096;
    private static final int BATCH_MAX = 64;

    private final BlockingQueue<AdminOperationLog> queue = new ArrayBlockingQueue<>(QUEUE_CAP);
    private final AtomicLong dropped = new AtomicLong();

    private final OperationLogService operationLogService;

    private Thread worker;

    @PostConstruct
    public void start() {
        worker = new Thread(this::drain, "admin-audit-writer");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    public void stop() {
        if (worker != null) {
            worker.interrupt();
        }
    }

    public void offer(AdminOperationLog record) {
        if (!queue.offer(record) && dropped.incrementAndGet() % 100 == 1) {
            log.error("审计日志队列已满，累计丢弃 {} 条", dropped.get());
        }
    }

    private void drain() {
        List<AdminOperationLog> buf = new ArrayList<>(BATCH_MAX);
        while (true) {
            try {
                buf.clear();
                buf.add(queue.take());
                queue.drainTo(buf, BATCH_MAX - 1);
                try {
                    operationLogService.saveBatch(buf);
                } catch (Exception e) {
                    log.error("审计日志落库失败，丢弃 {} 条", buf.size(), e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("审计消费者异常", e);
            }
        }
    }
}
