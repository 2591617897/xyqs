package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 私信会话。
 * 约定：user_a = min(双方 id)，user_b = max(双方 id)，
 * 会话成员固定后「谁是我、谁是对端」在查询时按当前登录用户动态判定。
 */
@Data
@TableName("t_message_conversation")
public class Conversation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userA;

    private Long userB;

    /** 会话发起人 */
    private Long initiatorId;

    /** 关联商品，0 表示非商品会话 */
    private Long goodsId;

    private String goodsTitle;

    private BigDecimal goodsPrice;

    private String goodsEmoji;

    /** 0已下架 1在售 2已售出 */
    private Integer goodsStatus;

    /** 1 匿名会话（表白墙回声） */
    private Integer anon;

    private String anonName;

    private Long lastMsgId;

    private String lastContent;

    private LocalDateTime lastTime;

    private Integer unreadA;

    private Integer unreadB;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
