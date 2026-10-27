package com.campus.goods.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.common.crypto.EncryptedStringTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 需求回应：看到需求的人说「我有货 / 我能帮」。
 *
 * <p>方向与 {@code t_goods_order} 相反 —— 意向单是买家找卖家，
 * 这里是「有货/有能力的人」找发布者，所以单独一张表。
 */
@Data
/** autoResultMap 必须为 true，否则 contact 的加密 TypeHandler 只在写入生效、读取仍是密文 */
@TableName(value = "t_demand_reply", autoResultMap = true)
public class DemandReply {

    /** 0 待查看 */
    public static final int STATUS_NEW = 0;
    /** 1 已读 */
    public static final int STATUS_READ = 1;
    /** 2 已忽略 */
    public static final int STATUS_IGNORED = 2;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long demandId;

    private Long replierId;

    private String replierName;

    private String replierAvatar;

    /** 我能提供什么 */
    private String message;

    /**
     * 回应者留下的联系方式（QQ / 微信 / 手机号）。
     *
     * <p>与 {@code t_goods_order.contact} 同一性质：为完成线下对接而临时提供给对方的
     * 个人联系方式，属敏感个人信息，落库加密（AES-256-GCM）。业务代码读到的仍是明文，
     * 直接查库或看备份的人只看到密文。
     */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String contact;

    /** 0待查看 1已读 2已忽略 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 所属需求标题快照，仅用于「我发出的回应」列表展示 */
    @TableField(exist = false)
    private String demandTitle;

    /** 所属需求类型，同上 */
    @TableField(exist = false)
    private Integer demandType;
}
