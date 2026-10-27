package com.campus.goods.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一次搜索的日志行（热词榜数据源）。
 *
 * <p>不追求「每一次搜索都留痕」：同一个人同一天同一个词只留一条，
 * 这样刷榜刷不动，日志量也可控。详见 {@code sql/migration-20261027-goods-hot-words.sql}。
 */
@Data
@TableName("t_goods_search_log")
public class GoodsSearchLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户输入原文（截断后），保留大写和原始空格，仅供排查用 */
    private String keyword;

    /** 归一化关键词：拆词后重新拼接，聚合口径 */
    private String normKeyword;

    /** 搜索者 id；游客记 0（唯一键需要非 NULL） */
    private Long userId;

    private Long schoolId;

    private String city;

    /** 该次搜索命中的商品条数 */
    private Integer resultCount;

    /** 按天分桶，唯一键的一部分 */
    private LocalDate searchDay;

    private LocalDateTime createdAt;
}
