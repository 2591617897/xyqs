package com.campus.common.ad;

import lombok.Data;

/**
 * 一种广告展示规格（＝小程序能渲染的一种固定高度）。
 *
 * <p><b>为什么要有规格，而不是让运营填「尺寸说明」</b>：广告在小程序里的高度是
 * {@code <image mode="aspectFill">} 的容器高度，由页面/组件写死，后台填什么都不会改变它。
 * 原来 {@code t_ad_slot.size_desc} 是自由文本框，运营填「800x600」也能保存成功，
 * 结果素材按 4:3 做、容器按 3.13:1 裁，两端的「尺寸」从来没有对上过，而且全程不报错。
 *
 * <p>现在尺寸收敛成有限几种规格：规格决定容器高度（{@link #heightRpx}），
 * 后台只能从 {@link AdSizeCatalog} 里选，小程序按同一份规格渲染——
 * 一处定义，三端（后台表单 / 接口下发 / 小程序容器）一致。
 */
@Data
public class AdSize {

    /** 规格编码，落库到 t_ad_slot.size_code */
    private final String code;

    /** 展示名（下拉里给运营看） */
    private final String label;

    /** 设计稿宽度（rpx）。小程序以 750rpx 为满宽，所以实际宽度恒为 750 */
    private final int widthRpx;

    /** 容器高度（rpx）—— 这个值就是小程序真实渲染的高度 */
    private final int heightRpx;

    /** 建议上传像素（按 1080 宽等比换算，够清晰又不至于太大） */
    private final String pixelHint;

    /** 适用位置，给运营判断用 */
    private final String purpose;

    /** 宽高比（宽 / 高，保留 2 位）。素材比例校验与前端预览用 */
    public double getRatio() {
        return Math.round((double) widthRpx / heightRpx * 100) / 100.0;
    }

    /** 「750×240」 */
    public String getSizeText() {
        return widthRpx + "×" + heightRpx;
    }
}
