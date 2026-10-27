package com.campus.message.service;

import com.campus.common.exception.BizException;
import com.campus.message.entity.Group;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发言频率策略（按群规模自适应）+ 两道内存闸门。
 *
 * <p><b>为什么不是 Redis：</b>本项目没有引入 Redis（登录限流 {@code LoginRateLimiter}
 * 也是进程内滑动窗口，服务端并未部署 Redis）。方案 v2.0 里写的 Redis 计数器在这里
 * 改成进程内实现，语义完全一致，代价是<b>多实例部署时阈值会放大到「实例数 × 阈值」</b>
 * —— 与 {@code LoginRateLimiter} 完全相同的取舍。真要横向扩容，
 * 把 {@link #gapUntil} / {@link #dayBucket} 换成 Redis 的 {@code SET NX EX} 与 {@code INCR}
 * 即可，本类对外方法签名不用改。
 *
 * <p>闸门顺序不能反：先看日限（不消耗间隔），再看间隔（消耗），最后才计数。
 * 否则「被日限拒绝」也会被算一次 60 秒冷却，用户会更困惑。
 */
@Slf4j
@Component
public class GroupRatePolicy {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 按群规模自适应。群越大，个人间隔越长 —— 这是万人群刷屏的唯一有效刹车。
     * 后台在 {@code t_group.rate_gap_sec > 0} 时可强制覆盖。
     */
    public record RateLimit(int gapSec, int dayMax) {
    }

    /** gap 闸：key = gid:uid → 下次可发言的时间戳（毫秒） */
    private final Map<String, Long> gapUntil = new ConcurrentHashMap<>();

    /** 日限闸：key = gid:uid → [yyyyMMdd, count] */
    private final Map<String, long[]> dayBucket = new ConcurrentHashMap<>();

    /** @所有人 日限：key = gid:uid:atall → [yyyyMMdd, count]，每人每天最多 3 次 */
    private final Map<String, long[]> atAllBucket = new ConcurrentHashMap<>();

    public static RateLimit resolve(Group g) {
        int forceGap = g.getRateGapSec() == null ? 0 : g.getRateGapSec();
        int forceDay = g.getRateDayMax() == null ? 0 : g.getRateDayMax();
        if (forceGap > 0) {
            return new RateLimit(forceGap, forceDay > 0 ? forceDay : 50);
        }
        int n = g.getMemberCount() == null ? 0 : g.getMemberCount();
        if (n <= 500) {
            return new RateLimit(10, 50);
        }
        if (n <= 2000) {
            return new RateLimit(20, 40);
        }
        if (n <= 5000) {
            return new RateLimit(40, 30);
        }
        return new RateLimit(60, 20);
    }

    /**
     * 校验并记账。管理员豁免间隔与日限（但仍受内容审核约束）。
     *
     * @throws BizException 429，带「还要等几秒」的人话
     */
    public void check(Group group, Long userId, boolean admin) {
        if (admin) {
            return;
        }
        RateLimit limit = resolve(group);
        String key = group.getId() + ":" + userId;
        long today = Long.parseLong(LocalDate.now().format(DAY));

        // ① 日限（只读判断，不消耗间隔）
        long[] bucket = dayBucket.computeIfAbsent(key, k -> new long[]{today, 0});
        synchronized (bucket) {
            if (bucket[0] != today) {
                bucket[0] = today;
                bucket[1] = 0;
            }
            if (bucket[1] >= limit.dayMax()) {
                throw BizException.tooManyRequests(
                        "今天发言已达上限（" + limit.dayMax() + " 条），明天再来聊吧");
            }
        }

        // ② 间隔（消耗）
        long now = System.currentTimeMillis();
        Long until = gapUntil.get(key);
        if (until != null && until > now) {
            long wait = (until - now + 999) / 1000;
            throw BizException.tooManyRequests("发言太频繁，请 " + wait + " 秒后再试");
        }
        gapUntil.put(key, now + limit.gapSec() * 1000L);

        // ③ 计数
        synchronized (bucket) {
            bucket[1]++;
        }
    }

    /**
     * @所有人 日限：每人每天 3 次。
     *
     * <p>这是「权力」而不是「便利」：1 万人的群一天被 @ 五次，用户会直接静音群，
     * 前面所有提醒设计都白做了。所以必须限量，且只对管理员开放。
     */
    public void checkAtAll(Long groupId, Long userId) {
        String key = groupId + ":" + userId + ":atall";
        long today = Long.parseLong(LocalDate.now().format(DAY));
        long[] bucket = atAllBucket.computeIfAbsent(key, k -> new long[]{today, 0});
        synchronized (bucket) {
            if (bucket[0] != today) {
                bucket[0] = today;
                bucket[1] = 0;
            }
            if (bucket[1] >= 3) {
                throw BizException.tooManyRequests("今天的 @所有人 已用 3 次，明天再试");
            }
            bucket[1]++;
        }
    }

    /** 我下一次可以发言的时间戳（前端倒计时用），不受限返回 0 */
    public long nextAllowedAt(Group group, Long userId, boolean admin) {
        if (admin) {
            return 0L;
        }
        Long until = gapUntil.get(group.getId() + ":" + userId);
        return until == null ? 0L : until;
    }

    /**
     * 定期清理，避免 Map 无限增长（E-09：不清理就是内存泄漏）。
     * 10 分钟一次，清掉已过期的 gap 与昨天的计数。
     */
    @Scheduled(initialDelay = 60_000L, fixedDelay = 600_000L)
    public void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> gi = gapUntil.entrySet().iterator();
        while (gi.hasNext()) {
            if (gi.next().getValue() < now) {
                gi.remove();
            }
        }
        long today = Long.parseLong(LocalDate.now().format(DAY));
        dayBucket.entrySet().removeIf(e -> e.getValue()[0] != today);
        atAllBucket.entrySet().removeIf(e -> e.getValue()[0] != today);
        if (gapUntil.size() > 50_000) {
            log.warn("[group-rate] 频率闸条目过多({})，建议检查是否有异常刷屏", gapUntil.size());
        }
    }
}
