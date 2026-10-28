package com.campus.gateway.security;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号状态校验：封禁 / 令牌版本。这是「封禁能不能真正生效」的关键环节。
 *
 * <h3>为什么需要它</h3>
 * <p>JWT 是无状态的，令牌签出去就收不回。后台把 {@code t_user.status} 改成 0，
 * 手里那张 72 小时的令牌一点变化都没有；而内容服务只校验令牌、不查库，
 * 于是「封禁后还能发三天内容」。更严重的是登录接口以前也不看状态，
 * 被封禁的人重开小程序就能换一张全新的令牌 —— 封禁按钮只有心理作用。
 *
 * <h3>为什么放在网关</h3>
 * <p>网关是唯一入口，在这里拦一次，8 个业务服务（goods / wall / post / message ...
 * 以及后续新增的）全部生效，不需要每个服务都去查一次库、也不会出现
 * 「新加的服务忘了加校验」这种典型遗漏。
 *
 * <h3>性能与可用性取舍</h3>
 * <ul>
 *   <li><b>60 秒缓存</b>：同一用户每分钟最多回源一次，热用户几乎无感。
 *       代价是封禁生效最多延迟 60 秒 —— 对「封禁」这种运营动作完全够用。</li>
 *   <li><b>fail-open</b>：campus-user 不可用（超时 / 非 2xx / 解析失败）时**放行**并打 warn。
 *       这里必须放行：把「用户服务抖动」放大成「全站 401」是不可接受的故障等级。
 *       真正的兜底仍然是「封禁 + 令牌版本」，等服务恢复后 60 秒内自动收紧。</li>
 * </ul>
 *
 * <h3>兼容性</h3>
 * <p>存量令牌没有 {@code tv} claim，按 0 处理；{@code t_user.token_version} 默认也是 0，
 * 所以本次升级<b>不会把任何人踢下线</b>。
 */
@Slf4j
@Component
public class AccountStateChecker {

    /** 单个用户状态缓存时长。60 秒是「封禁及时性」与「回源压力」的折中。 */
    private static final long CACHE_TTL_MS = 60_000L;

    /** 缓存条目上限，防止被海量 userId 撑爆内存（正常量级远达不到）。 */
    private static final int MAX_ENTRIES = 20_000;

    /** 回源超时。必须短：它是每个「缓存未命中」请求的额外延迟。 */
    private static final Duration TIMEOUT = Duration.ofMillis(800);

    private final WebClient webClient;

    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    public AccountStateChecker(@LoadBalanced WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    /** 库里读到的账号状态。 */
    private record Cached(int status, int tokenVersion, long at) {
    }

    /** 校验结论：放行 / 封禁 / 令牌失效。 */
    public enum Verdict {
        /** 正常 */
        OK,
        /** 账号被封禁（status=0）→ 返回 403，避免小程序把 401 当掉线自动清 token 后反复重登 */
        BANNED,
        /** 令牌版本落后（注销 / 解封后旧令牌）→ 返回 401，引导重新登录换新令牌 */
        STALE
    }

    /**
     * 判断本次请求能否放行。
     *
     * @param userId   令牌里的用户 id
     * @param claimTv  令牌里的 tv claim（缺失按 0）
     * @return 结论；正常情况永不返回 null
     */
    public Mono<Verdict> check(Long userId, @Nullable Integer claimTv) {
        int tv = claimTv == null ? 0 : claimTv;
        long now = System.currentTimeMillis();

        Cached hit = cache.get(userId);
        if (hit != null && now - hit.at() < CACHE_TTL_MS) {
            return Mono.just(judge(hit, tv));
        }

        return fetch(userId)
                .map(state -> {
                    cache.put(userId, state);
                    sweep(now);
                    return judge(state, tv);
                })
                // 回源失败 → fail-open（见类注释：宁可短暂放松，也不能全站 401）
                .onErrorResume(e -> {
                    log.warn("[security] 账号状态回源失败，本次放行 userId={} err={}", userId, e.toString());
                    return Mono.just(Verdict.OK);
                });
    }

    /** 供运营侧感知：封禁用户时的日志落在网关，便于和后台操作时间对齐 */
    private Verdict judge(Cached state, int claimTv) {
        if (state.status() == 0) {
            return Verdict.BANNED;
        }
        if (claimTv != state.tokenVersion()) {
            return Verdict.STALE;
        }
        return Verdict.OK;
    }

    /**
     * 回源 campus-user。
     *
     * <p>返回 {@code data == null} 表示「用户不存在」——这种是**确定结论**，走拒绝；
     * 只有网络 / 超时 / 非 2xx 才由 {@link #check} 的 onErrorResume 兜成放行。
     */
    @SuppressWarnings("unchecked")
    private Mono<Cached> fetch(Long userId) {
        return webClient.get()
                .uri("http://campus-user/internal/users/{id}/auth-state", userId)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(TIMEOUT)
                .map(body -> {
                    Object code = body.get("code");
                    if (code == null || !"0".equals(String.valueOf(code))) {
                        // 服务端明确报错：当作「查不到」，交给上层按异常路径放行
                        throw new IllegalStateException("auth-state 返回非 0：" + code);
                    }
                    Object data = body.get("data");
                    if (!(data instanceof Map<?, ?> m)) {
                        // data 为 null = 用户不存在（或已满保留期被清理）：
                        // 用 tokenVersion=-1 让它落进 STALE → 401，引导重新登录，
                        // 而不是 BANNED（403 会让用户以为被封禁了）。
                        log.warn("[security] 令牌对应的用户不存在，判定失效 userId={}", userId);
                        return new Cached(1, -1, System.currentTimeMillis());
                    }
                    return new Cached(intOf(m.get("status"), 1), intOf(m.get("tokenVersion"), 0),
                            System.currentTimeMillis());
                });
    }

    /**
     * status: 1 正常 / 0 封禁。库里的 status 允许为 NULL（历史数据），按正常处理。
     */
    private int intOf(Object v, int fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void sweep(long now) {
        if (cache.size() < MAX_ENTRIES) {
            return;
        }
        cache.entrySet().removeIf(e -> now - e.getValue().at() >= CACHE_TTL_MS);
    }

    /** 封禁 / 解封后由后台触发时无需手动清缓存：最多 60 秒自然过期。 */
    public void invalidate(Long userId) {
        cache.remove(userId);
    }
}
