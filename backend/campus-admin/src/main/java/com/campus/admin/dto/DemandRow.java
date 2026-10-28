package com.campus.admin.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台需求广场列表行（t_demand + 学校名）。
 *
 * <p>与 {@link GoodsRow} 并列：一个是「我要出的」，一个是「我要找的」。
 * typeName / categoryName 由后端拼好，后台页面不自己维护分类枚举。
 */
@Data
public class DemandRow {

    private Long id;

    /** 1求物 2求帮忙 */
    private Integer type;

    /** 中文大类，列表直接展示 */
    private String typeName;

    private String title;

    private String description;

    private Integer categoryId;

    /** 中文分类名 */
    private String categoryName;

    /** 预算 / 报酬；NULL = 面议 */
    private BigDecimal budget;

    private String expectTime;

    private String location;

    private String images;

    private Long userId;

    private String authorName;

    private Long schoolId;

    private String schoolName;

    /** school 仅本校 / city 同市 */
    private String visibility;

    /** 0已下架 1进行中 2已完成 */
    private Integer status;

    private Integer viewCount;

    private Integer replyCount;

    private LocalDateTime createdAt;
}
