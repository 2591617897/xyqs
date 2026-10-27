package com.campus.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.client.SecurityAdminClient;
import com.campus.admin.entity.ContentAudit;
import com.campus.admin.entity.SensitiveWord;
import com.campus.admin.mapper.ContentAuditMapper;
import com.campus.admin.mapper.SensitiveWordMapper;
import com.campus.common.search.SearchKit;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词库维护与文本检测。
 *
 * <p>词库很小（千级），没必要上前缀树：整表载入内存做 contains 匹配足够快，
 * 关键是<b>改词后要手动刷新缓存</b>，否则运营改了词但线上还在用旧词库。
 * 所以每次增删改都会自动 reload，并提供一个显式的 reload 接口兜底。
 *
 * <p>level 语义：1仅记录（留痕不拦）2替换成 *** 3禁止发布。
 * 禁止发布要由发布链路调用 {@link #check(String)} 后自行拒绝，
 * 本服务不直接改任何业务表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveService {

    private final SensitiveWordMapper wordMapper;
    private final ContentAuditMapper contentAuditMapper;
    private final SecurityAdminClient securityAdminClient;

    /** 只读缓存，reload 时整体替换引用，避免读写加锁 */
    @Getter
    private volatile List<SensitiveWord> cache = List.of();

    /** 启动时只载入本机，不去打扰可能还没起来的 auth */
    @PostConstruct
    public void init() {
        loadWords();
    }

    /** 运营改词后调用：本机 + auth 一起刷新 */
    public void reload() {
        loadWords();
        pushRemote();
    }

    private void loadWords() {
        List<SensitiveWord> list = wordMapper.selectList(Wrappers.<SensitiveWord>lambdaQuery()
                .eq(SensitiveWord::getStatus, 1));
        cache = list;
        log.info("[admin] 敏感词缓存已刷新，共 {} 条", list.size());
    }

    /**
     * 通知 campus-auth 立即重载词库。
     *
     * <p>真正拦用户内容的是 auth 里的 {@code LocalSensitiveGuard}，它自己也每 60s 轮询一次，
     * 这里只是把「运营改完词立刻生效」的等待抹掉。所以调用失败完全可以忽略——
     * 顶多多等一个刷新周期，绝不能反过来让后台的增删改词失败。
     */
    private void pushRemote() {
        try {
            securityAdminClient.reloadWords();
        } catch (Exception e) {
            log.warn("[sensitive] 通知 campus-auth 刷新词库失败，将由定时刷新兜底：{}", e.getMessage());
        }
    }

    public R.Page<SensitiveWord> pageWords(String keyword, Integer level, Integer status, long page, long size) {
        LambdaQueryWrapper<SensitiveWord> qw = Wrappers.<SensitiveWord>lambdaQuery()
                .eq(level != null, SensitiveWord::getLevel, level)
                .eq(status != null, SensitiveWord::getStatus, status)
                .orderByDesc(SensitiveWord::getLevel)
                .orderByDesc(SensitiveWord::getId);
        SearchKit.andAllWords(qw, keyword, SensitiveWord::getWord);
        IPage<SensitiveWord> p = wordMapper.selectPage(new Page<>(page, size), qw);
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SensitiveWord word) {
        if (word.getWord() == null || word.getWord().isBlank()) {
            throw BizException.of("敏感词不能为空");
        }
        word.setWord(word.getWord().trim());
        if (wordMapper.selectCount(Wrappers.<SensitiveWord>lambdaQuery()
                .eq(SensitiveWord::getWord, word.getWord())) > 0) {
            throw BizException.of("该敏感词已存在");
        }
        if (word.getLevel() == null) {
            word.setLevel(2);
        }
        if (word.getStatus() == null) {
            word.setStatus(1);
        }
        if (word.getLevel() == 2 && (word.getReplaceTo() == null || word.getReplaceTo().isBlank())) {
            word.setReplaceTo("***");
        }
        wordMapper.insert(word);
        reload();
        return word.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SensitiveWord req) {
        SensitiveWord exist = wordMapper.selectById(id);
        if (exist == null) {
            throw BizException.notFound("敏感词不存在");
        }
        SensitiveWord patch = new SensitiveWord();
        patch.setId(id);
        patch.setLevel(req.getLevel());
        patch.setReplaceTo(req.getReplaceTo());
        patch.setCategory(req.getCategory());
        patch.setStatus(req.getStatus());
        patch.setRemark(req.getRemark());
        wordMapper.updateById(patch);
        reload();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (wordMapper.deleteById(id) == 0) {
            throw BizException.notFound("敏感词不存在");
        }
        reload();
    }

    /**
     * 检测文本命中的敏感词。
     *
     * @return 命中的词列表，空表示通过
     */
    public List<SensitiveWord> check(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<SensitiveWord> hits = new ArrayList<>();
        for (SensitiveWord w : cache) {
            if (w.getWord() != null && text.contains(w.getWord())) {
                hits.add(w);
            }
        }
        return hits;
    }

    /** 按 level=2 的规则把命中词替换掉，返回处理后的文本 */
    public String filter(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        for (SensitiveWord w : cache) {
            if (w.getWord() == null || w.getWord().isEmpty()) {
                continue;
            }
            if (Integer.valueOf(2).equals(w.getLevel()) && result.contains(w.getWord())) {
                result = result.replace(w.getWord(), w.getReplaceTo() == null ? "***" : w.getReplaceTo());
            }
        }
        return result;
    }

    /**
     * 命中留痕：把「谁的内容命中了什么词」写进处置流水，
     * 便于事后复盘词库是否误伤。不自动处置，只留证据。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordHit(String targetType, Long targetId, List<SensitiveWord> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        for (SensitiveWord w : hits) {
            ContentAudit audit = new ContentAudit();
            audit.setTargetType(targetType);
            audit.setTargetId(targetId);
            audit.setAction("WARN");
            audit.setReasonType(w.getCategory());
            audit.setReason("命中敏感词：" + w.getWord());
            audit.setSource(2);
            contentAuditMapper.insert(audit);
        }
    }
}
