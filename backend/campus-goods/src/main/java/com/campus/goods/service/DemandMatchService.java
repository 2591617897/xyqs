package com.campus.goods.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.search.SearchKit;
import com.campus.goods.entity.Demand;
import com.campus.goods.entity.Goods;
import com.campus.goods.mapper.DemandMapper;
import com.campus.goods.mapper.GoodsMapper;
import com.campus.goods.mapper.SchoolRegionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 需求 ↔ 闲置 互通推荐。
 *
 * <p>解决的是两个方向上的同一件事：
 * <ul>
 *   <li>有人在找自行车 → 把他可能想买的在售闲置推到需求详情页（发布者不用再自己去搜）</li>
 *   <li>我在卖自行车 → 把「有人在找自行车」推到闲置详情页（卖家可以主动去回应）</li>
 * </ul>
 *
 * <p>匹配刻意做得简单可解释：**只对标题拆词后做包含匹配并计数**，不上分词器、
 * 不上向量。校园场景的数据量是百级到千级，词面匹配已经够用，
 * 而且命中了几个词能直接说清楚（「匹配度」可解释，用户不会觉得是黑箱）。
 *
 * <p>做法是「先取候选池，再在内存里打分」：池子限制在本校/同市的在售内容里取最近
 * N 条（有索引、有上限），不会因为全表扫描把列表接口拖慢。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemandMatchService {

    /** 候选池上限：够覆盖校园级数据量，又不会让打分循环变成负担 */
    private static final int POOL = 300;
    /** 返回条数：卡片位就那么大，超过 3 条会把详情撑长 */
    private static final int LIMIT = 3;

    private final GoodsMapper goodsMapper;
    private final DemandMapper demandMapper;
    private final SchoolRegionMapper schoolRegionMapper;

    public record MatchGoodsVO(Long id, String title, BigDecimal price, List<String> imageList) {
    }

    public record MatchDemandVO(Long id, Integer type, String title, BigDecimal budget) {
    }

    /**
     * 需求详情 → 可能符合的在售闲置。
     *
     * @param viewerSchoolId 浏览者学校，用于可见范围过滤（不能把外校「仅本校」的闲置推给他）
     */
    public List<MatchGoodsVO> matchGoods(Long demandId, Long viewerSchoolId) {
        Demand demand = demandMapper.selectById(demandId);
        if (demand == null || !StringUtils.hasText(demand.getTitle())) {
            return List.of();
        }
        List<String> words = SearchKit.words(demand.getTitle());
        if (words.isEmpty()) {
            return List.of();
        }
        List<Long> schoolIds = visibleSchoolIds(viewerSchoolId);
        if (schoolIds == null) {
            return List.of();
        }
        List<Goods> pool = goodsMapper.selectList(Wrappers.<Goods>lambdaQuery()
                .eq(Goods::getStatus, 1)
                .in(Goods::getSchoolId, schoolIds)
                .orderByDesc(Goods::getCreatedAt)
                .last("limit " + POOL));
        // 可见范围：同市范围内，他校的「仅本校」内容仍不可见
        List<Goods> candidates = pool.stream()
                .filter(g -> viewerSchoolId == null
                        || viewerSchoolId.equals(g.getSchoolId())
                        || "city".equals(g.getVisibility()))
                .toList();
        return candidates.stream()
                .map(g -> new Scored<>(g, score(words, g.getTitle(), g.getDescription())))
                .filter(s -> s.score > 0)
                .sorted(Comparator
                        .comparingInt((Scored<Goods> s) -> -s.score)
                        .thenComparing(s -> sameSchool(viewerSchoolId, s.raw.getSchoolId()) ? 0 : 1)
                        .thenComparing(s -> s.raw.getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(LIMIT)
                .map(s -> new MatchGoodsVO(s.raw.getId(), s.raw.getTitle(), s.raw.getPrice(), split(s.raw.getImages())))
                .toList();
    }

    /** 闲置详情 → 有人在找这类东西 */
    public List<MatchDemandVO> matchDemands(Long goodsId, Long viewerSchoolId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null || !StringUtils.hasText(goods.getTitle())) {
            return List.of();
        }
        List<String> words = SearchKit.words(goods.getTitle());
        if (words.isEmpty()) {
            return List.of();
        }
        List<Long> schoolIds = visibleSchoolIds(viewerSchoolId);
        if (schoolIds == null) {
            return List.of();
        }
        List<Demand> pool = demandMapper.selectList(Wrappers.<Demand>lambdaQuery()
                .eq(Demand::getStatus, Demand.STATUS_OPEN)
                .in(Demand::getSchoolId, schoolIds)
                .orderByDesc(Demand::getCreatedAt)
                .last("limit " + POOL));
        List<Demand> candidates = pool.stream()
                .filter(d -> viewerSchoolId == null
                        || viewerSchoolId.equals(d.getSchoolId())
                        || "city".equals(d.getVisibility()))
                .toList();
        return candidates.stream()
                .map(d -> new Scored<>(d, score(words, d.getTitle(), d.getDescription())))
                .filter(s -> s.score > 0)
                .sorted(Comparator
                        .comparingInt((Scored<Demand> s) -> -s.score)
                        .thenComparing(s -> sameSchool(viewerSchoolId, s.raw.getSchoolId()) ? 0 : 1)
                        .thenComparing(s -> s.raw.getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(LIMIT)
                .map(s -> new MatchDemandVO(s.raw.getId(), s.raw.getType(), s.raw.getTitle(), s.raw.getBudget()))
                .toList();
    }

    // ---------------- 内部 ----------------

    /**
     * 浏览者能看到哪些学校的内容：本校 + 同市其它校。
     * 学校身份缺失（游客）时返回 null —— 沿用既有「不按学校收窄」的行为，
     * 不去猜游客属于哪个市。
     */
    private List<Long> visibleSchoolIds(Long viewerSchoolId) {
        if (viewerSchoolId == null) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        ids.add(viewerSchoolId);
        String city = schoolRegionMapper.selectCityBySchoolId(viewerSchoolId);
        if (StringUtils.hasText(city)) {
            for (Long sid : schoolRegionMapper.selectSchoolIdsByCity(city)) {
                if (!ids.contains(sid)) {
                    ids.add(sid);
                }
            }
        }
        return ids;
    }

    private boolean sameSchool(Long viewerSchoolId, Long targetSchoolId) {
        return viewerSchoolId != null && viewerSchoolId.equals(targetSchoolId);
    }

    /**
     * 词面打分：标题命中一个词 +2，描述命中 +1。
     * 标题权重更高，是因为「自行车」出现在标题里的，几乎就是用户要找的东西；
     * 只在描述里出现的往往只是顺带一提。
     */
    private int score(List<String> words, String title, String description) {
        String t = title == null ? "" : title.toLowerCase();
        String d = description == null ? "" : description.toLowerCase();
        int s = 0;
        for (String w : words) {
            String lw = w.toLowerCase();
            if (lw.isEmpty()) {
                continue;
            }
            if (t.contains(lw)) {
                s += 2;
            } else if (d.contains(lw)) {
                s += 1;
            }
        }
        return s;
    }

    private List<String> split(String images) {
        if (!StringUtils.hasText(images)) {
            return List.of();
        }
        return List.of(images.split(","));
    }

    /** 打分中间态：raw 保留原对象，避免为了取字段再查一次 */
    private record Scored<T>(T raw, int score) {
    }
}
