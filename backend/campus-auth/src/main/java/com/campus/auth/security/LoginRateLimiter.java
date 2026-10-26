package com.campus.auth.security;

import com.campus.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录频次限制（滑动窗口）。
 *
 * <p>C 端登录走微信 {@code code2Session}，没有口令可爆破，但仍有三个现实风险：
 * <ol>
 *   <li>别人拿你的 code 重放 / 脚本批量换取令牌，把 openid 空间刷爆；</li>
 *   <li>单个 IP 高频登录，配合「登录即建号」把 t_user 灌成垃圾表；</li>
 *   <li>code2Session 有日调用配额，被刷等于把小程序登录打挂。</li>
 * </ol>
 *
 * <p>因此按 <b>IP</b> 与 <b>openid</b> 两个维度各记一条滑动窗口，超限直接 429。
 * 阈值故意留得比较宽松（正常用户一分钟点不了 10 次登录），只拦机器行为。
 *
 * <p><b>单机实现</b>：计数在进程内存里，多实例部署时每个实例各算一份，
 * 实际阈值会被放大到「实例数 × 阈值」。当前 campus-auth 是单实例；
 * 若将来要横向扩容，把 {@link #hit} 换成 Redis 的 {@code ZADD/ZREMRANGEBYSCORE} 即可，
 * 接口签名不用改。
 */
@Slf4j
@Component
public class LoginRateLimiter {

    /** 窗口内允许的登录次数：单 IP */
    @Value("${campus.auth.login.max-per-ip:30}")
    private int maxPerIp;

    /** 窗口内允许的登录次数：单 openid */
    @Value("${campus.auth.login.max-per-openid:10}")
    private int maxPerOpenid;

    /** 窗口长度（秒） */
    @Value("${campus.auth.login.window-seconds:60}")
    private long windowSeconds;

    /** 触发清理的条目数上限，防止被海量 IP 撑爆内存 */
    private static final int MAX_ENTRIES = 20_000;

    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    /**
     * 记一次登录尝试，超限抛 429。
     *
     * @param ip     客户端 IP（可为空）
     * @param openid 微信 openid（可为空，取不到时只按 IP 限）
     */
    public void check(String ip, String openid) {
        long now = System.currentTimeMillis();
        if (StringUtils.hasText(ip)) {
            hit("ip:" + ip, now, maxPerIp, "登录过于频繁，请稍后再试");
        }
        // openid 维度只在拿到真实 openid 后校验（mock 模式下的合成 openid 也走同一条路）
        if (StringUtils.hasText(openid)) {
            hit("oid:" + openid, now, maxPerOpenid, "该账号登录过于频繁，请稍后再试");
        }
        sweep(now);
    }

    private void hit(String key, long now, int limit, String msg) {
        long windowMs = windowSeconds * 1000L;
        Deque<Long> q = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                log.warn("[security] 登录频次超限 key={} count={} limit={} window={}s",
                        key, q.size(), limit, windowSeconds);
                throw BizException.tooManyRequests(msg);
            }
            q.addLast(now);
        }
    }

    /** 清掉窗口内已经空了的桶，避免长期运行后 Map 无限增长 */
    private void sweep(long now) {
        if (buckets.size() < MAX_ENTRIES) {
            return;
        }
        long windowMs = windowSeconds * 1000L;
        buckets.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            synchronized (q) {
                return q.isEmpty() || now - q.peekLast() > windowMs;
            }
        });
    }
}
