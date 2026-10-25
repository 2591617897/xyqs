package com.campus.post.dto;

import lombok.Data;

/**
 * 对外投放的一条广告素材。
 *
 * <p>刻意只暴露渲染与跳转必需的字段：审核意见、内部备注等一律不外泄。
 * campus-post 侧的 mapper 直接把它作为结果类型接收（借 map-underscore-to-camel-case
 * 自动映射），因此不需要再建一份实体副本。
 *
 * <p>linkType 取值（与后台素材配置一致）：0无跳转 1帖子 2商品 3外链 4小程序页面，
 * linkValue 分别是空 / 帖子ID / 商品ID / URL / 页面路径。
 */
@Data
public class AdMaterialVO {

    private Long id;

    private String title;

    /** 素材图片，绝对地址 */
    private String image;

    /** 0无跳转 1帖子 2商品 3外链 4小程序页面 */
    private Integer linkType;

    private String linkValue;

    private Integer sort;

    /** 所属广告位编码，便于埋点回溯 */
    private String slotCode;
}
