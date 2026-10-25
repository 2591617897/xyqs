package com.campus.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.auth.entity.SensitiveWord;
import com.campus.auth.mapper.SensitiveWordMapper;
import com.campus.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 本地敏感词过滤：运营在后台维护的词库，真正作用到用户的发帖/评论/昵称/私信上。
 *
 * <p>为什么放在这里而不是 campus-admin：
 * campus-admin 是后台，小程序<b>永远访问不到</b>它。而所有 UGC 的写入汇聚点
 * 都在 campus-auth 的 {@code /internal/security}，所以这里才是唯一正确的落点。
 *
 * <p>与微信 {@code msg_sec_check} 的分工：
 * <ul>
 *   <li><b>本地词</b> —— 同步、零延迟、零费用，且<b>不依赖任何外部凭据</b>。
 *       没配 appid/appsecret 时它照样工作，这一点很关键：本地开发、CI、
 *       以及线上凭据失效期间，词库仍在守着底线。</li>
 *   <li><b>微信审核</b> —— 覆盖语义、隐晦表达与政治敏感，是兜底的第二道防线。</li>
 * </ul>
 * 顺序是「先本地，后微信」：本地先净化再送去微信，还能省下调用额度。
 *
 * <p>level 语义（与后台文案保持一致）：
 * <ul>
 *   <li>1 提示 —— 不改文本，只记一条 WARN 流水，留给运营复盘；</li>
 *   <li>2 替换 —— 按 {@code replace_to} 替换（没配就 {@code ***}）；</li>
 *   <li>3 禁止发布 —— 直接抛 400，不落库。</li>
 * </ul>
 *
 * <p>缓存刷新：<b>定时轮询</b>而不是让 admin 推消息。
 * 轮询让 campus-auth 对 campus-admin 零依赖——后台挂了、重启了、迁移了都不影响
 * 这套过滤；代价是改词后最多 {refreshSeconds} 秒生效，运营可接受。
 * admin 改词时会顺带调一次 {@link #reload} 做即时刷新，失败也无妨。
 */
@Slf4j
@Service
public class LocalSensitiveGuard {

    /** 替换兜底：后台没配 replace_to 时用它 */
    private static final String DEFAULT_REPLACE = "***";
    /** 处置动作 / 来源，与 WxSecurityService 保持一致 */
    private static final String ACTION_REJECT = "REJECT";
    private static final String ACTION_WARN = "WARN";
    private static final int SOURCE_SYSTEM = 2;

    private final SensitiveWordMapper wordMapper;
    private final ContentAuditTrailWriter auditWriter;

    /** 词库很小（千级），整份常驻内存做 contains 即可，不必上 Trie */
    private volatile List<SensitiveWord> cache = List.of();

    @Value("${campus.sensitive.refresh-seconds:60}")
    private long refreshSeconds;

    private ScheduledExecutorService scheduler;

    public LocalSensitiveGuard(SensitiveWordMapper wordMapper, ContentAuditTrailWriter auditWriter) {
        this.wordMapper = wordMapper;
        this.auditWriter = auditWriter;
    }

    @PostConstruct
    public void init() {
        reload();
        // 单守护线程定时轮询；用线程池而非 @Scheduled，
        // 是为了不在 auth 上全局打开 Scheduling —— 影响面更小，也便于关闭
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sensitive-word-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::safeReload, refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 重新载入词库。
     *
     * <p>查库失败时<b>保留旧缓存</b>而不是清空：宁可让已生效的词继续拦着，
     * 也不能因为一次数据库抖动就让全站词库「消失」。
     */
    public void reload() {
        List<SensitiveWord> list = wordMapper.selectEnabled();
        cache = list;
        log.info("[sensitive] 词库已载入，共 {} 条", list.size());
    }

    private void safeReload() {
        try {
            reload();
        } catch (Exception e) {
            log.warn("[sensitive] 定时刷新词库失败，继续使用旧缓存：{}", e.getMessage());
        }
    }

    /**
     * 本地词净化：level 3 直接拦、level 2 替换成对应占位符、level 1 只留痕。
     *
     * @param userId  提交者，用于留痕
     * @param text    待处理文本（可能为 null）
     * @param bizType 业务类型，用于留痕（SecurityGuard.BIZ_*）
     * @return 处理后的文本；原样返回的情况：文本为空、词库为空、无任何命中
     * @throws BizException 命中 level 3 时抛 400
     */
    public String purify(Long userId, String text, String bizType) {
        if (text == null || text.isBlank()) {
            return text;
        }
        List<SensitiveWord> words = cache;
        if (words.isEmpty()) {
            return text;
        }

        String result = text;
        List<SensitiveWord> hit = new ArrayList<>(2);
        for (SensitiveWord w : words) {
            String word = w.getWord();
            if (word == null || word.isBlank() || !result.contains(word)) {
                continue;
            }
            hit.add(w);
            Integer level = w.getLevel() == null ? 2 : w.getLevel();
            if (level == 3) {
                auditWriter.record(bizType, null, userId, text, ACTION_REJECT,
                        "命中本地敏感词（禁止发布）：" + word);
                throw BizException.of("内容包含不允许发布的敏感词，请修改后再试");
            }
            if (level == 2) {
                String to = w.getReplaceTo() == null || w.getReplaceTo().isBlank()
                        ? DEFAULT_REPLACE : w.getReplaceTo();
                result = result.replace(word, to);
            }
            // level 1：不改文本，只在下面统一记 WARN
        }

        if (!hit.isEmpty()) {
            countHit(hit);
            auditWriter.record(bizType, null, userId, text, ACTION_WARN,
                    "命中本地敏感词：" + hit.stream()
                            .map(w -> w.getWord() + "(L" + (w.getLevel() == null ? 2 : w.getLevel()) + ")")
                            .reduce((a, b) -> a + "、" + b).orElse(""));
        }
        return result;
    }

    /** 命中计数（尽力而为：统计不准也不能让发布失败） */
    private void countHit(List<SensitiveWord> hit) {
        try {
            for (SensitiveWord w : hit) {
                wordMapper.incrHit(w.getId());
            }
        } catch (Exception e) {
            log.warn("[sensitive] 命中计数更新失败，已忽略：{}", e.getMessage());
        }
    }

    /** 当前词库大小，探活用 */
    public int size() {
        return cache.size();
    }

    /**
     * 手动检测用（后台「试一下」按钮）。与 {@link #purify} 的区别是不留痕、不计数、不拦。
     */
    public List<SensitiveWord> detect(String text) {
        List<SensitiveWord> words = cache;
        List<SensitiveWord> found = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        for (SensitiveWord w : words) {
            if (w.getWord() != null && !w.getWord().isBlank() && text.contains(w.getWord())) {
                found.add(w);
            }
        }
        return found;
    }
}
