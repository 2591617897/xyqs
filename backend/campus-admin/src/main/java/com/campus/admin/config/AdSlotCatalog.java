package com.campus.admin.config;

import com.campus.admin.dto.AdSlotOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 广告位编码注册表——后台与小程序的唯一契约。
 *
 * <p><b>为什么要有这张表</b>：投放链路是「后台建广告位（code）→ 小程序按 code 拉素材」，
 * code 拼错一个字符，小程序就一条都拿不到，但两边都不报错。历史上已经出现过
 * 运营把编码填成 {@code 1} 的情况：后台显示「上架」，实际永远不展示。
 * 把编码做成白名单后，能选的编码就是小程序真的会去拉的编码。
 *
 * <p><b>position 由 code 推导，不再让前端传</b>：位置原本是独立的表单字段，
 * 意味着可以建出「位置=表白墙、code=home_banner」这种自相矛盾的广告位，
 * 而投放查询只认 code 不认 position，position 实际是个装饰字段。
 * 现在两者 1:1 绑定，只有一处真相。
 *
 * <p>新增一个投放位＝在 {@link #ALL} 里加一行 + 小程序页面接上 {@code <ad-banner code="..." />}，
 * 顺序不要动：position 值会写进历史数据。
 */
public final class AdSlotCatalog {

    private AdSlotCatalog() {
    }

    /** 全部受支持的广告位，按 position 升序 */
    private static final List<AdSlotOption> ALL = build();

    private static List<AdSlotOption> build() {
        List<AdSlotOption> list = new ArrayList<>();
        list.add(option("home_banner", "首页Banner", 1, true,
                "750x300", "首页顶部，多条自动轮播"));
        list.add(option("zone_top", "功能区顶部", 2, true,
                "750x240", "底部「功能区」页最上方，单条展示"));
        list.add(option("wall_top", "表白墙顶部", 3, true,
                "750x240", "表白墙列表顶部，单条展示"));
        list.add(option("goods_top", "闲置列表顶部", 4, true,
                "750x240", "二手市场列表顶部（分类栏上方）"));
        list.add(option("splash", "启动开屏", 5, false,
                "750x1334", "小程序暂未接入：可创建，但不能上架"));
        list.add(option("mine_banner", "个人中心", 6, true,
                "750x200", "「我的」页用户卡下方，单条展示"));
        return List.copyOf(list);
    }

    private static AdSlotOption option(String code, String name, int position, boolean connected,
                                       String sizeHint, String pageHint) {
        AdSlotOption o = new AdSlotOption();
        o.setCode(code);
        o.setName(name);
        o.setPosition(position);
        o.setConnected(connected);
        o.setSizeHint(sizeHint);
        o.setPageHint(pageHint);
        return o;
    }

    /** 全部可选编码（后台下拉直接返回这个） */
    public static List<AdSlotOption> all() {
        return ALL;
    }

    public static Optional<AdSlotOption> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String key = code.trim();
        return ALL.stream().filter(o -> o.getCode().equals(key)).findFirst();
    }

    /** 编码是否受支持（脏数据 / 手工插库的编码一律 false） */
    public static boolean supports(String code) {
        return find(code).isPresent();
    }

    /** 小程序是否已接入该编码 */
    public static boolean connected(String code) {
        return find(code).map(AdSlotOption::getConnected).orElse(false);
    }
}
