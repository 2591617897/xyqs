package com.campus.common.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 多词搜索的**唯一实现**。
 *
 * <p>旧做法是把用户输入整串塞进 {@code LIKE '%整串%'}，于是「苹果 手机」永远搜不到
 * 「苹果手机 九成新」——用户以为搜索坏了，其实是语义不同。这里统一成
 * <b>按空白拆词 → 每个词都要命中 → 词与词之间 AND，同一词的多个列之间 OR</b>：
 *
 * <pre>
 *   keyword = "苹果 手机"
 *   SQL     = AND (title LIKE '%苹果%' OR description LIKE '%苹果%')
 *             AND (title LIKE '%手机%' OR description LIKE '%手机%')
 * </pre>
 *
 * <p><b>适用边界</b>：只用于「文本内容」搜索（标题 / 正文 / 昵称 / 校名）。
 * 手机号、工单号、账号、角色 code 这类**标识符精确搜索不要用本类** ——
 * 把它们拆成多词只会让结果变空（它们本身就是单个 token）。
 *
 * <p>拆词规则（前后端必须一致，见 miniprogram/utils/kw.js）：
 * <ul>
 *   <li>分隔符：任意空白，含全角空格 U+3000、Tab、换行</li>
 *   <li>去重：忽略大小写保序去重（"iPhone iphone" 只搜一次，省一次 LIKE）</li>
 *   <li>限量：最多 {@link #MAX_WORDS} 个词，防止有人粘一整段话把 SQL 撑成几十个 LIKE</li>
 *   <li>截断：单个词最多 {@link #MAX_WORD_LEN} 个字符（按码点，不会切坏 emoji）</li>
 *   <li>丢弃：只由通配符 {@code %} {@code _} 组成的词（否则 LIKE '%%%' 恒真，条件白给）</li>
 * </ul>
 *
 * <p>⚠️ 这里不做分词、不做同义词、不做拼音。真要做到「手机」能搜到「iPhone」得换 ES，
 * 当前数据量下 MySQL LIKE 够用，别为了显得高级把架构搞复杂。
 */
public final class SearchKit {

    /** 最多拆几个词。超过就只取前几个，宁可少搜也不让 SQL 膨胀。 */
    public static final int MAX_WORDS = 6;

    /** 单个词的字符数上限（码点）。 */
    public static final int MAX_WORD_LEN = 24;

    private static final Pattern SEP = Pattern.compile("[\\s\\u3000]+");

    /** 只由 LIKE 通配符组成的词，没有检索意义 */
    private static final Pattern WILDCARD_ONLY = Pattern.compile("^[%_]+$");

    private SearchKit() {
    }

    /**
     * 拆词。永不返回 null，无有效词时返回空列表。
     *
     * @param keyword 用户原始输入
     */
    public static List<String> words(String keyword) {
        if (keyword == null) {
            return Collections.emptyList();
        }
        String raw = keyword.trim();
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        // 用 LinkedHashMap 去重：既保留首次出现的顺序，又能在 O(1) 判断重复
        Map<String, String> uniq = new LinkedHashMap<>();
        for (String part : SEP.split(raw)) {
            String word = cut(part);
            if (word.isEmpty() || WILDCARD_ONLY.matcher(word).matches()) {
                continue;
            }
            // key 用小写做去重键，value 保留用户原始大小写（LIKE 在 MySQL 默认不区分大小写，
            // 但保留原样更利于排查日志）
            uniq.putIfAbsent(word.toLowerCase(Locale.ROOT), word);
            if (uniq.size() >= MAX_WORDS) {
                break;
            }
        }
        return uniq.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(uniq.values()));
    }

    /** 有没有可用的搜索词（等价于「用户真的输了东西」）。 */
    public static boolean hasWords(String keyword) {
        return !words(keyword).isEmpty();
    }

    /**
     * 给 MyBatis-Plus 的 LambdaQueryWrapper 叠加多词 AND 条件。
     *
     * <p>单字段时生成 {@code AND c LIKE '%w1%' AND c LIKE '%w2%'}；
     * 多字段时每个词内部 OR，词之间 AND。
     *
     * <p>调用方**不需要**再判空，方法内部已处理：无有效词直接返回，不改动 wrapper。
     *
     * @param w      目标 wrapper
     * @param keyword 用户输入
     * @param fields 参与匹配的列，至少一个
     */
    @SafeVarargs
    public static <T> void andAllWords(LambdaQueryWrapper<T> w, String keyword, SFunction<T, ?>... fields) {
        List<String> words = words(keyword);
        if (w == null || words.isEmpty() || fields == null || fields.length == 0) {
            return;
        }
        for (String word : words) {
            // 每个词独立 and(...) 包一层括号：不能写成平铺的 or()，
            // 否则会和相邻的 status/schoolId 等条件串成「A OR B AND C」的优先级事故。
            w.and(q -> {
                for (int i = 0; i < fields.length; i++) {
                    if (i == 0) {
                        q.like(fields[i], word);
                    } else {
                        q.or().like(fields[i], word);
                    }
                }
            });
        }
    }

    /** 按码点截断，避免把 emoji / 生僻字切成半个。 */
    private static String cut(String s) {
        String t = s.trim();
        if (t.isEmpty()) {
            return t;
        }
        if (t.codePointCount(0, t.length()) <= MAX_WORD_LEN) {
            return t;
        }
        return t.substring(0, t.offsetByCodePoints(0, MAX_WORD_LEN));
    }
}
