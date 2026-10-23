package com.campus.message.dto;

import lombok.Data;

/** 会话里关联的宝贝卡片（快照，不回源查商品） */
@Data
public class GoodsCard {

    private Long id;

    private String title;

    /** 字符串价格，前端直接展示 */
    private String price;

    private String emoji;

    private String statusText;

    public static GoodsCard of(Long id, String title, String price, String emoji, Integer status) {
        GoodsCard c = new GoodsCard();
        c.setId(id);
        c.setTitle(title);
        c.setPrice(price);
        c.setEmoji(emoji == null || emoji.isEmpty() ? "🛍️" : emoji);
        c.setStatusText(statusText(status));
        return c;
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return "在售中";
        }
        return switch (status) {
            case 0 -> "已下架";
            case 2 -> "已售出";
            default -> "在售中";
        };
    }
}
