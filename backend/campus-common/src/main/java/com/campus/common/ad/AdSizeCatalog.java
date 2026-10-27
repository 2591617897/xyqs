package com.campus.common.ad;

import java.util.List;

/**
 * 广告展示规格注册表 —— 小程序「能渲染成什么样」的唯一清单。
 *
 * <p>这张表<b>只能由小程序侧决定</b>：每一种规格对应小程序里一种真实的容器高度，
 * 后台只是「选一种」，不能创造新尺寸。加规格的正确姿势是先在
 * {@code miniprogram/components/ad-banner} 里确认这个高度渲染得好看、不裁切，
 * 再回来加一行；反过来（后台先加一种规格）会让小程序按陌生高度渲染，
 * 卡片要么被撑高、要么把素材裁得只剩中间一条。
 *
 * <p>谁在用：
 * <ul>
 *   <li>campus-admin：广告位表单的规格下拉（{@code GET /ads/slots/sizes}），保存时校验；</li>
 *   <li>campus-post：{@code GET /ads/{code}} 给每条素材下发 {@code displayHeight}；</li>
 *   <li>miniprogram：{@code <ad-banner>} 与首页 Banner 直接按下发的高度渲染。</li>
 * </ul>
 *
 * <p>放在 campus-common 而不是各服务各存一份：复制粘贴的常量表迟早会漂移，
 * 而这类漂移在业务上表现为「后台显示 750×300、真机渲染 240」这种没人说得清的 bug。
 */
public final class AdSizeCatalog {

    /**
     * 兜底规格。
     *
     * <p>取 240：它是列表顶部横版（表白墙 / 闲置 / 功能区）的通用高度，
     * 覆盖了绝大多数投放位；老数据 {@code size_code} 为空时也按它渲染。
     */
    public static final String DEFAULT_CODE = "banner_240";

    private static final List<AdSize> ALL = List.of(
            new AdSize("banner_300", "通栏大图 750×300", 750, 300,
                    "1080×432", "首页顶部轮播，视觉最重的一档"),
            new AdSize("banner_240", "标准横版 750×240", 750, 240,
                    "1080×346", "列表顶部（表白墙 / 闲置 / 功能区），最通用"),
            new AdSize("banner_200", "紧凑横版 750×200", 750, 200,
                    "1080×288", "个人中心，卡片高度受限的页面"),
            new AdSize("banner_160", "细条横幅 750×160", 750, 160,
                    "1080×230", "内容流中间插入，尽量少占屏")
    );

    private AdSizeCatalog() {
    }

    /** 全部规格，按高度从大到小（下拉顺序稳定） */
    public static List<AdSize> all() {
        return ALL;
    }

    /** 按编码查；未知编码返回 null（调用方自行决定兜底还是报错） */
    public static AdSize of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String key = code.trim();
        for (AdSize s : ALL) {
            if (s.getCode().equals(key)) {
                return s;
            }
        }
        return null;
    }

    /** 编码是否受支持（脏数据 / 手工插库一律 false） */
    public static boolean supports(String code) {
        return of(code) != null;
    }

    /** 未知或为空时返回兜底规格，永不返回 null */
    public static AdSize orDefault(String code) {
        AdSize s = of(code);
        return s != null ? s : of(DEFAULT_CODE);
    }

    /** 渲染高度（rpx）—— 小程序直接用这个值撑容器 */
    public static int heightOf(String code) {
        return orDefault(code).getHeightRpx();
    }

    /** 宽高比（宽 / 高），素材比例校验用 */
    public static double ratioOf(String code) {
        return orDefault(code).getRatio();
    }

    /**
     * 由编码生成「尺寸说明」文案，如 {@code 750×240 · 3.13:1 · 建议 1080×346}。
     *
     * <p>{@code t_ad_slot.size_desc} 仍保留这一列（列表展示、历史兼容），
     * 但内容改成<b>由 size_code 派生</b>，不再接受手填——手填就是两套尺寸口径的开始。
     */
    public static String descOf(String code) {
        AdSize s = orDefault(code);
        return s.getSizeText() + " · " + s.getRatio() + ":1 · 建议 " + s.getPixelHint();
    }
}
