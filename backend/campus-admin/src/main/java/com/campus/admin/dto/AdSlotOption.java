package com.campus.admin.dto;

import lombok.Data;

/**
 * 广告位可选编码（后台「广告位管理」下拉用）。
 *
 * <p>编码是<b>小程序与后台之间唯一的契约</b>：小程序写死要拉 {@code home_banner}，
 * 后台就得建一个 code 完全相同的广告位。以前编码是自由文本输入框，运营填
 * {@code homeBanner} / {@code 首页} 都能保存成功，但一个用户也看不到——数据对不上，
 * 界面却毫无提示。所以这里把编码收敛成受控枚举，可选即可用。
 */
@Data
public class AdSlotOption {

    /** 编码（小程序按它拉取，创建后不可修改） */
    private String code;

    /** 展示名，同时作为广告位默认名称 */
    private String name;

    /** 位置（与 t_ad_slot.position 一致，由编码唯一决定） */
    private Integer position;

    /**
     * 小程序是否已接入。
     * false 表示后台可以建、可以审，但<b>不允许上架</b>——避免运营以为已投放成功。
     */
    private Boolean connected;

    /** 建议尺寸，选中后自动填到「尺寸说明」 */
    private String sizeHint;

    /** 小程序里的具体位置，给运营看的一句话说明 */
    private String pageHint;
}
