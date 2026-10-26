package com.campus.promote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推广观看流水。
 *
 * <p>一次「点推广 → 看完广告 → 结算」一行。weight 记的是<b>衰减前的满分值</b>，
 * 真实有效分由 {@code expire_at} 折算，所以流水写入后不再修改，
 * 只由衰减任务读它重算内容表上的 promote_score。
 */
@Data
@TableName("t_promote_record")
public class PromoteRecord {

    /** 内容类型：GOODS 闲置 / POST 帖子 / WALL 表白墙 */
    public static final String GOODS = "GOODS";
    public static final String POST = "POST";
    public static final String WALL = "WALL";

    /** 0 待观看（已签发凭证，等客户端结算） */
    public static final int STATUS_PENDING = 0;
    /** 1 已结算 */
    public static final int STATUS_DONE = 1;
    /** 2 已作废（超时 / 主动放弃） */
    public static final int STATUS_VOID = 2;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String targetType;

    private Long targetId;

    private String nonce;

    private Integer weight;

    private Integer status;

    private String adUnitId;

    /** 签发时间：最短观看时长与凭证有效期都以它为起点 */
    private LocalDateTime issuedAt;

    private LocalDateTime finishAt;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    public static boolean supported(String targetType) {
        return GOODS.equals(targetType) || POST.equals(targetType) || WALL.equals(targetType);
    }

    /** 内容定位（衰减任务去重用） */
    public record Target(String targetType, Long targetId) {
    }
}
