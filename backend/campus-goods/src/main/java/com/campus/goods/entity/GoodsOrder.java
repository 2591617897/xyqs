package com.campus.goods.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_goods_order")
public class GoodsOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long goodsId;

    private Long buyerId;

    private Long sellerId;

    private String buyerName;

    private String message;

    private String contact;

    /** 0待卖家确认 1已确认 2已完成 3已取消 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 商品标题快照，仅用于列表展示 */
    @TableField(exist = false)
    private String goodsTitle;
}
