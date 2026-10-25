package com.campus.admin.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台二手商品列表行。
 */
@Data
public class GoodsRow {

    private Long id;

    private String title;

    private String description;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String images;

    private Long sellerId;

    private String sellerName;

    private Long schoolId;

    private String schoolName;

    private Integer categoryId;

    private Integer conditionLevel;

    /** 0已下架 1在售 2已售出 */
    private Integer status;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private LocalDateTime createdAt;
}
