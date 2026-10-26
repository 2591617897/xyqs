package com.campus.goods.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.common.crypto.EncryptedStringTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
/** autoResultMap 必须为 true，否则 contact 的加密 TypeHandler 只在写入生效、读取仍是密文 */
@TableName(value = "t_goods_order", autoResultMap = true)
public class GoodsOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long goodsId;

    private Long buyerId;

    private Long sellerId;

    private String buyerName;

    private String message;

    /**
     * 买家留下的联系方式（QQ / 微信 / 手机号）。
     *
     * <p>这条数据的性质是「为了完成交易而临时提供给对方的个人联系方式」，
     * 属于个人信息保护法里的敏感个人信息范畴，落库加密（AES-256-GCM）。
     * 业务代码读到的仍然是明文，任何直接查库/看备份的人都只看到密文。
     */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String contact;

    /** 0待卖家确认 1已确认 2已完成 3已取消 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 商品标题快照，仅用于列表展示 */
    @TableField(exist = false)
    private String goodsTitle;
}
