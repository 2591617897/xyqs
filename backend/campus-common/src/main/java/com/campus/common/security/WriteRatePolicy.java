package com.campus.common.security;

import com.campus.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UGC 写入频控（发布 / 评论 / 回应 / 下单）。
 *
 * <h3>为什么需要它</h3>
 * <p>项目里唯一的内容频控是校友群的 {@code GroupRatePolicy}（群聊 10~60 秒间隔 + 日上限）。
 * <b>发帖、树洞/表白、闲置、需求、评论这五条主线一个都没有</b>：
 * 内容安全只拦「违规词」，不拦「合法但高频」。一个已认证账号可以在一分钟内
 * 把整屏刷满，把真实内容全挤下去——这是最容易被真实用户投诉的问题（第三轮审计 P1-2）。
 *
 * <h3>阈值怎么定的</h3>
 * <p>原则是「正常用户永远碰不到，机器行为一定撞墙」：
 * <ul>
 *   <li>发帖：30 秒一次、日 30 条（正常同学一天发不了 30 条，但重复发「招聘」「代课」的一定会）；</li>
 *   <li>树洞/表白：60 秒一次、日 20 条（匿名场景刷屏动机最强，间隔给足）；</li>
 *   <li>闲置/需求：120 秒一次、日 15 条（发布成本高，正常不会连发）；</li>
 *   <li>评论：10 秒一次、日 200 条（评论区互动频繁，不能卡太死）；</li>
 *   <li>下单：10 秒一次、日 100 条（防脚本骚扰卖家，正常买家一天点不了 100 次）。</li>
 * </ul>
 * 阈值都是常量而不是配置项：它们属于产品口径，改动应该走代码评审，
 * 而不是线上改个环境变量就把风控关掉。需要整体停用（压测 / 演示）时用 {@code campus.write-rate.enabled=false}。
 *
 * <h3>关于「无 Redis」</h3>
 * <p>与 {@code LoginRateLimiter}、{@code GroupRatePolicy} 一致，计数在进程内存里。
 * 单实例部署完全够用；一旦横向扩容，实际阈值会被放大到「实例数 × 阈值」，
 * 届时把 {@link Bucket} 换成 Redis 的 ZSET/INCR 即可，调用方签名不用改。
 */
@Slf4j
@Component
public class WriteRatePolicy {

    /** 受控的写入动作。新增业务在这里加一行，别在业务代码里自己写死间隔。 */
    public enum Action {
        PUBLISH_POST("发帖", Duration.ofSeconds(30), 30),
        PUBLISH_WALL("发布树洞/表白", Duration.ofSeconds(60), 20),
        PUBLISH_GOODS("发布闲置", Duration.ofSeconds(120), 15),
        PUBLISH_DEMAND("发布需求", Duration.ofSeconds(120), 15),
        COMMENT("评论", Duration.ofSeconds(10), 200),
        REPLY("回应需求", Duration.ofSeconds(60), 30),
        CREATE_ORDER("下单", Duration.ofSeconds(10), 100);

        private final String label;
        private final Duration gap;
        private final int dayMax;

        Action(String label, Duration gap, int dayMax) {
            this.label = label;
            this.gap = gap;
            this.dayMax = dayMax;
        }

        public String label() {
            return label;
        }

        public Duration gap() {
            return gap;
        }

        public int dayMax() {
            return dayMax;
        }
    }

    /** 条目上限：正常用户量级远达不到，超了整体清理一次防内存膨胀 */
    private static final int MAX_ENTRIES = 50_000;

    /** 总开关。仅压测 / 演示可关，生产保持 true。 */
    @Value("${campus.write-rate.enabled:true}")
    private boolean enabled;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * 记一次写入尝试；超限抛 429。
     *
     * @param action 动作类型
     * @param userId 当前用户（{@code null} 直接放行——走到这里之前都已经过 requireVerifiedUserId）
     */
    public void check(Action action, Long userId) {
        if (!enabled || action == null || userId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Bucket b = buckets.computeIfAbsent(action.name() + ":" + userId, k -> new Bucket());
        synchronized (b) {
            // ① 间隔：防止「连点发布」把列表刷满
            if (b.lastAt > 0 && now - b.lastAt < action.gap().toMillis()) {
                long wait = (action.gap().toMillis() - (now - b.lastAt)) / 1000 + 1;
                log.warn("[security] 写入频控（间隔）action={} userId={} wait={}s", action.name(), userId, wait);
                throw BizException.tooManyRequests(action.label() + "太频繁了，请 " + wait + " 秒后再试");
            }
            // ② 日上限：跨天自动重置，不需要定时任务清理
            LocalDate today = LocalDate.now();
            if (!today.equals(b.day)) {
                b.day = today;
                b.dayCount = 0;
            }
            if (b.dayCount >= action.dayMax()) {
                log.warn("[security] 写入频控（日上限）action={} userId={} count={}",
                        action.name(), userId, b.dayCount);
                throw BizException.tooManyRequests("今天" + action.label() + "的次数已达上限，明天再来吧");
            }
            b.lastAt = now;
            b.dayCount++;
        }
        sweep(now);
    }

    /** 清掉「昨天之前且已经过了间隔窗口」的桶，避免长期运行后 Map 无限增长 */
    private void sweep(long now) {
        if (buckets.size() < MAX_ENTRIES) {
            return;
        }
        LocalDate today = LocalDate.now();
        buckets.entrySet().removeIf(e -> {
            Bucket b = e.getValue();
            synchronized (b) {
                return b.day != null && b.day.isBefore(today)
                        && now - b.lastAt > Duration.ofHours(24).toMillis();
            }
        });
    }

    /** 单个用户的写入窗口 */
    private static final class Bucket {
        private long lastAt;
        private LocalDate day;
        private int dayCount;
    }
}
