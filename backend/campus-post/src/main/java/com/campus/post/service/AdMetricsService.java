package com.campus.post.service;

import com.campus.common.security.ClientIp;
import com.campus.post.mapper.OperateQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 广告曝光 / 点击计数（去重 + 聚合刷写）。
 *
 * <h3>为什么不能像以前那样直接 UPDATE</h3>
 * <p>以前 {@code GET /api/ads/{code}} 每返回一条素材就 {@code UPDATE ... view_count+1}，
 * 点击接口同样直接 +1，两个入口都<b>不需要登录、不去重、不采样、不限流</b>。
 * 于是有两个问题（第三轮审计 P1-1）：
 * <ol>
 *   <li><b>数据不可信</b>：一条 curl 循环就能把点击量刷到十万，
 *       广告主看到的转化率是假的，这个模块本来就是给商家看效果的；</li>
 *   <li><b>可用性</b>：1000 rps 的 GET 会打出 3000 次/秒的 {@code t_ad_material} 行更新，
 *       行锁竞争会拖慢整个 campus-post —— 一个公开只读接口把写库量放大了三个数量级。</li>
 * </ol>
 *
 * <h3>现在怎么做</h3>
 * <ul>
 *   <li><b>去重</b>：同一 IP 对同一素材，曝光 10 分钟算一次，点击 24 小时算一次。
 *       用户下拉刷新首页不该刷出 5 次曝光；同一个人反复点同一个 Banner 也只算一次转化。</li>
 *   <li><b>聚合</b>：计数先进进程内 {@link AtomicInteger}，每 60 秒把累加值一次性刷进库。
 *       请求路径上<b>零 DB 写</b>，公开 GET 再怎么被刷也只是几次内存自增。</li>
 * </ul>
 *
 * <h3>取舍</h3>
 * <p>进程内实现，与项目里 {@code LoginRateLimiter} / {@code GroupRatePolicy} 的取舍一致
 * （本项目没有 Redis）。代价有两条，都是可接受的：
 * <ul>
 *   <li>多实例部署时每个实例各自 flush，总量仍然正确（各加各的），只是去重粒度变成「每实例」；</li>
 *   <li>进程被 kill 时，最近 60 秒的计数会丢。效果指标允许这种误差——</li>
 * </ul>
 * 反过来说，如果这段计数逻辑本身抛异常，绝不能影响首页渲染，所以 flush 全程 catch。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdMetricsService {

    /** 同一 IP 对同一素材的曝光去重窗口 */
    private static final long VIEW_DEDUPE_MS = 10 * 60_000L;
    /** 同一 IP 对同一素材的点击去重窗口（按天算，跨天自然失效） */
    private static final long CLICK_DEDUPE_MS = 24 * 3600_000L;
    /** 去重表条目上限，超了整体清理一次，防止被海量 IP 撑爆内存 */
    private static final int MAX_DEDUPE_ENTRIES = 50_000;
    /** 素材存在性校验的缓存时长：避免每次点击都 SELECT 一次 */
    private static final long MATERIAL_CHECK_MS = 10 * 60_000L;

    private final OperateQueryMapper operateMapper;

    /** 待刷写的曝光增量 */
    private final Map<Long, AtomicInteger> viewDelta = new ConcurrentHashMap<>();
    /** 待刷写的点击增量 */
    private final Map<Long, AtomicInteger> clickDelta = new ConcurrentHashMap<>();
    /** 去重表：key = view:{materialId}:{ip} */
    private final Map<String, Long> viewSeen = new ConcurrentHashMap<>();
    /** 去重表：key = click:{materialId}:{ip}:{yyyy-MM-dd} */
    private final Map<String, Long> clickSeen = new ConcurrentHashMap<>();
    /** 素材存在性缓存：materialId → 上次校验时间（只缓存「存在」的，不存在的每次都会查，量极小） */
    private final Map<Long, Long> materialCheckedAt = new ConcurrentHashMap<>();

    /**
     * 记一次曝光。重复曝光（同 IP 同素材 10 分钟内）直接丢弃。
     *
     * <p>全程不抛异常：曝光统计失败绝不能影响首页，调用方也不该 try。
     */
    public void recordView(Long materialId) {
        if (materialId == null) {
            return;
        }
        try {
            String key = "view:" + materialId + ":" + clientIp();
            if (!firstTime(viewSeen, key, VIEW_DEDUPE_MS)) {
                return;
            }
            viewDelta.computeIfAbsent(materialId, k -> new AtomicInteger()).incrementAndGet();
        } catch (Exception e) {
            log.debug("[ad] 曝光计数忽略：{}", e.getMessage());
        }
    }

    /**
     * 记一次点击。同 IP 同素材 24 小时内只算一次。
     *
     * @return 本次是否被计入（false = 判定为重复点击，调用方无需区分，仅便于测试）
     */
    public boolean recordClick(Long materialId) {
        if (materialId == null) {
            return false;
        }
        try {
            // 存在性校验：挡住拿任意 id 刷计数（缓存 10 分钟，不会每次都查库）
            if (!materialExists(materialId)) {
                return false;
            }
            String key = "click:" + materialId + ":" + clientIp() + ":" + LocalDate.now();
            if (!firstTime(clickSeen, key, CLICK_DEDUPE_MS)) {
                return false;
            }
            clickDelta.computeIfAbsent(materialId, k -> new AtomicInteger()).incrementAndGet();
            return true;
        } catch (Exception e) {
            log.debug("[ad] 点击计数忽略：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 每 60 秒把内存里的增量刷进库。
     *
     * <p>{@code getAndSet(0)} 先置零再更新：期间新来的计数会累加到「新的 0」上，
     * 下一轮再刷，不会丢也不会重复。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void flush() {
        flushMap(viewDelta, true);
        flushMap(clickDelta, false);
        sweep();
    }

    private void flushMap(Map<Long, AtomicInteger> map, boolean view) {
        for (Map.Entry<Long, AtomicInteger> e : map.entrySet()) {
            int delta = e.getValue().getAndSet(0);
            if (delta <= 0) {
                continue;
            }
            try {
                if (view) {
                    operateMapper.incrViewBy(e.getKey(), delta);
                } else {
                    operateMapper.incrClickBy(e.getKey(), delta);
                }
            } catch (Exception ex) {
                // 刷写失败就丢掉这一批：效果指标不值得重试把库压垮
                log.warn("[ad] {}计数刷写失败 materialId={} delta={} err={}",
                        view ? "曝光" : "点击", e.getKey(), delta, ex.getMessage());
            }
        }
    }

    /** 去重表定期清理，防止长期运行后 Map 无限增长 */
    private void sweep() {
        if (viewSeen.size() > MAX_DEDUPE_ENTRIES) {
            long now = System.currentTimeMillis();
            viewSeen.entrySet().removeIf(e -> now - e.getValue() > VIEW_DEDUPE_MS);
        }
        if (clickSeen.size() > MAX_DEDUPE_ENTRIES) {
            long now = System.currentTimeMillis();
            clickSeen.entrySet().removeIf(e -> now - e.getValue() > CLICK_DEDUPE_MS);
        }
        if (materialCheckedAt.size() > MAX_DEDUPE_ENTRIES) {
            long now = System.currentTimeMillis();
            materialCheckedAt.entrySet().removeIf(e -> now - e.getValue() > MATERIAL_CHECK_MS);
        }
    }

    /**
     * 首次出现返回 true 并记录时间；窗口内重复出现返回 false。
     */
    private boolean firstTime(Map<String, Long> seen, String key, long windowMs) {
        long now = System.currentTimeMillis();
        Long last = seen.get(key);
        if (last != null && now - last < windowMs) {
            return false;
        }
        seen.put(key, now);
        return true;
    }

    private boolean materialExists(Long materialId) {
        long now = System.currentTimeMillis();
        Long checked = materialCheckedAt.get(materialId);
        if (checked != null && now - checked < MATERIAL_CHECK_MS) {
            return true;
        }
        if (operateMapper.countMaterial(materialId) <= 0) {
            return false;
        }
        materialCheckedAt.put(materialId, now);
        return true;
    }

    /**
     * 取客户端 IP 作为去重维度。拿不到（非 Web 线程）时退化为 {@code "unknown"}，
     * 此时等价于「全站共用同一个桶」——不会崩，只是去重更粗。
     */
    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return ClientIp.resolve(attrs.getRequest());
        }
        return "unknown";
    }
}
