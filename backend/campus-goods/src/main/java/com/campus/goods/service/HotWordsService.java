package com.campus.goods.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.campus.common.client.SecurityGuard;
import com.campus.common.search.SearchKit;
import com.campus.common.security.UserContext;
import com.campus.goods.entity.GoodsSearchLog;
import com.campus.goods.mapper.GoodsSearchLogMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 闲置搜索热词：「大家都在搜」的数据来源。
 *
 * <p>链路很短：搜索接口把词写进 {@code t_goods_search_log}，热词接口按近 N 天聚合。
 * 没有 Redis，没有定时预计算——校园体量下聚合查询是毫秒级，
 * 加一层缓存（{@link #CACHE_MS}）就够了，少一个组件就少一处要维护的东西。
 *
 * <p>三条「不做事」的约定，都是为了不把垃圾词捧上首页：
 * <ol>
 *   <li>搜索结果 0 条的词不进榜（点进去是空列表，比没有热词更糟）；</li>
 *   <li>3 秒内的连续输入只记最后一个词（边打边搜会留下「考」「考研」「考研书」三条）；</li>
 *   <li>展示前做前缀折叠 + 本地敏感词过滤。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotWordsService {

    /** 冷启动兜底：日志还没攒够时用这批词，保证搜索面板永远有东西可点 */
    private static final List<String> FALLBACK = List.of(
            "自行车", "考研书", "显示器", "电动车", "台灯", "耳机", "球拍", "被子");

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 30;
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;
    /** 至少被搜这么多次才上榜，滤掉「手滑搜了一次」的偶发词 */
    private static final int MIN_HITS = 2;
    /** 一次搜索打 3 个字符，3 秒内的中间态没必要全部落库 */
    private static final long SETTLE_MS = 3_000L;
    /** 榜单缓存：热词不需要「实时」，5 分钟足够新鲜 */
    private static final long CACHE_MS = 5 * 60_000L;
    /** 日志保留天数，由 {@code SearchLogCleanTask} 清理 */
    public static final int KEEP_DAYS = 90;

    private static final int MIN_LEN = 2;
    private static final int MAX_LEN = 24;
    /**
     * 允许出现在热词里的字符：中英文、数字、空格、少量常用符号。
     *
     * <p>热词会直接展示给所有人，所以宁可错杀——一个「🔥包邮🔥」式的词上了榜首，
     * 整个面板的观感就没了。
     */
    private static final Pattern ALLOWED =
            Pattern.compile("[\\u4e00-\\u9fa5a-zA-Z0-9 　·\\-_.+&/、]+");
    /** 纯数字多半是型号 / 价格，不作品牌词 */
    private static final Pattern DIGITS_ONLY = Pattern.compile("[0-9 　]+");

    private final GoodsSearchLogMapper searchLogMapper;
    private final SecurityGuard securityGuard;

    /** 当前生效的榜单快照；整体替换，读侧无锁 */
    private volatile Snapshot snapshot;

    /** 每个用户「待落库」的那一次搜索；被新搜索覆盖即丢弃 */
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();

    private final AtomicLong seq = new AtomicLong();

    /** 单线程延迟执行器：只做「3 秒后落库」，不涉及业务，daemon 线程不挡停机 */
    private final ScheduledExecutorService settler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hot-words-settle");
        t.setDaemon(true);
        return t;
    });

    /**
     * 记录一次搜索。
     *
     * <p>调用方必须传<b>当前请求线程</b>里的关键词——方法内部会立刻取 {@link UserContext#userId()}，
     * 落库却发生在 3 秒后的调度线程上（那里拿不到 ThreadLocal）。
     *
     * @param keyword     用户输入的关键词，空白直接忽略
     * @param resultCount 本次搜索命中条数
     */
    public void record(String keyword, long resultCount, Long schoolId, String city) {
        String norm = normalize(keyword);
        if (norm == null) {
            return;
        }
        Long current = UserContext.userId();
        // 游客记 0：唯一键 (search_day, user_id, norm_keyword) 需要非 NULL，
        // 副作用是「所有游客同一天同一个词只算 1 次」，热词对游客略微保守，可接受。
        Long uid = current == null ? 0L : current;

        long my = seq.incrementAndGet();
        pending.put(uid, new Pending(keyword.trim(), norm, resultCount, schoolId, city, my));
        try {
            settler.schedule(() -> settle(uid, my), SETTLE_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // 停机中：少记一条热词，不影响任何功能
            pending.remove(uid);
        }
    }

    /**
     * 取热词榜。
     *
     * @param days  统计窗口天数，默认 7，上限 30
     * @param limit 返回条数，默认 8，上限 20
     */
    public List<String> top(Integer days, Integer limit) {
        int d = clamp(days == null ? DEFAULT_DAYS : days, 1, MAX_DAYS);
        int l = clamp(limit == null ? DEFAULT_LIMIT : limit, 1, MAX_LIMIT);
        long now = System.currentTimeMillis();

        Snapshot snap = snapshot;
        if (snap != null && snap.days() == d && snap.limit() == l && snap.expireAt() > now) {
            return snap.words();
        }
        List<String> words = load(d, l);
        snapshot = new Snapshot(d, l, words, now + CACHE_MS);
        return words;
    }

    /** 清理过期日志，返回删除条数 */
    public int cleanExpired() {
        return searchLogMapper.deleteBefore(LocalDate.now().minusDays(KEEP_DAYS));
    }

    private List<String> load(int days, int limit) {
        List<String> picked = new ArrayList<>(limit);
        try {
            LocalDate from = LocalDate.now().minusDays(days - 1L);
            // 多取候选：前缀折叠和敏感词过滤都会再砍掉一些，只要 limit 条会凑不满
            List<GoodsSearchLogMapper.HotWordRow> rows =
                    searchLogMapper.topWords(from, MIN_HITS, Math.min(limit * 4, 80));
            for (GoodsSearchLogMapper.HotWordRow row : foldPrefixes(rows)) {
                if (picked.size() >= limit) {
                    break;
                }
                String word = row.getWord();
                if (word != null && !sensitive(word)) {
                    picked.add(word);
                }
            }
        } catch (Exception e) {
            log.warn("[hot-words] 榜单聚合失败，回落到默认热词：{}", e.getMessage());
        }
        // 冷启动 / 聚合失败时补齐默认词：面板空着比词不新更糟
        for (String word : FALLBACK) {
            if (picked.size() >= limit) {
                break;
            }
            if (!picked.contains(word)) {
                picked.add(word);
            }
        }
        return List.copyOf(picked);
    }

    /**
     * 前缀折叠：把人打字过程中的中间态从榜上摘掉。
     *
     * <p>规则——若存在更长的词 B 以短词 A 开头，且 B 的人次<b>不低于</b> A，
     * 就认定 A 只是 B 的输入中间态，丢掉 A。
     *
     * <p>为什么是「不低于」而不是「接近」：打「考研书」的人必然经过「考研」，
     * 且同人同日去重后中间态的贡献最多等于长词本身。反过来，「手机」100 次 vs「手机壳」30 次
     * 时 30 &lt; 100，短词会保留——真正独立的短词不会被误伤。
     */
    private List<GoodsSearchLogMapper.HotWordRow> foldPrefixes(List<GoodsSearchLogMapper.HotWordRow> rows) {
        List<GoodsSearchLogMapper.HotWordRow> byLength = new ArrayList<>(rows);
        // 长词先判定，短词只需和「已经保留的长词」比较
        byLength.sort((a, b) -> {
            int diff = b.getWord().length() - a.getWord().length();
            return diff != 0 ? diff : hits(b) - hits(a);
        });

        List<GoodsSearchLogMapper.HotWordRow> kept = new ArrayList<>(byLength.size());
        for (GoodsSearchLogMapper.HotWordRow shortWord : byLength) {
            boolean intermediate = false;
            for (GoodsSearchLogMapper.HotWordRow longWord : kept) {
                if (longWord.getWord().startsWith(shortWord.getWord())
                        && hits(longWord) >= hits(shortWord)) {
                    intermediate = true;
                    break;
                }
            }
            if (!intermediate) {
                kept.add(shortWord);
            }
        }
        kept.sort((a, b) -> hits(b) - hits(a));
        return kept;
    }

    /**
     * 热词要展示给所有人，所以过一遍本地敏感词库。
     *
     * <p>用 {@code purify} 而不是 {@code checkText}：后者命中会抛 400，
     * 一个脏词不该让整个热词接口失败，这里只需要「知道它脏，然后不展示」。
     * 命中 level-3 时 purify 会抛 400，同样按「脏」处理。
     */
    private boolean sensitive(String word) {
        try {
            String clean = securityGuard.purify(null, word, SecurityGuard.BIZ_GOODS);
            return clean != null && !clean.equals(word);
        } catch (Exception e) {
            return true;
        }
    }

    /** 把待落库的那一条写进库；期间用户又搜了别的词则直接跳过 */
    private void settle(Long uid, long my) {
        Pending p = pending.get(uid);
        if (p == null || p.seq() != my) {
            return;
        }
        pending.remove(uid, p);
        try {
            GoodsSearchLog row = new GoodsSearchLog();
            row.setId(IdWorker.getId());
            row.setKeyword(p.keyword().length() > 64 ? p.keyword().substring(0, 64) : p.keyword());
            row.setNormKeyword(p.norm());
            row.setUserId(uid);
            row.setSchoolId(p.schoolId());
            row.setCity(p.city());
            row.setResultCount((int) Math.max(0, Math.min(p.resultCount(), Integer.MAX_VALUE)));
            row.setSearchDay(LocalDate.now());
            searchLogMapper.insertIgnore(row);
        } catch (Exception e) {
            log.warn("[hot-words] 搜索词落库失败（不影响搜索本身）：{}", e.getMessage());
        }
    }

    /** 归一化：拆词 → 重新拼接 → 长度 / 字符 / 纯数字校验；不合格返回 null */
    private String normalize(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        List<String> words = SearchKit.words(keyword);
        if (words.isEmpty()) {
            return null;
        }
        // 用拆词结果重新拼接：「苹果　　18」和「苹果 18」在榜上就是同一个词
        String norm = String.join(" ", words);
        int len = norm.codePointCount(0, norm.length());
        if (len < MIN_LEN || len > MAX_LEN) {
            return null;
        }
        if (!ALLOWED.matcher(norm).matches() || DIGITS_ONLY.matcher(norm).matches()) {
            return null;
        }
        return norm;
    }

    private static int hits(GoodsSearchLogMapper.HotWordRow row) {
        return row.getHits() == null ? 0 : row.getHits();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @PreDestroy
    public void shutdown() {
        settler.shutdownNow();
    }

    /** 待落库的一次搜索 */
    private record Pending(String keyword, String norm, long resultCount,
                           Long schoolId, String city, long seq) {
    }

    /** 榜单快照 */
    private record Snapshot(int days, int limit, List<String> words, long expireAt) {
    }
}
