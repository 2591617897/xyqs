package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告素材。挂在广告位下，一个广告位可轮播多张。
 */
@Data
@TableName("t_ad_material")
public class AdMaterial {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long slotId;

    private String slotName;

    private String title;

    private String image;

    /** 0无跳转 1帖子 2商品 3外链 4小程序页面 */
    private Integer linkType;

    private String linkValue;

    private Integer sort;

    /** 0下架 1上架 */
    private Integer status;

    /** 0待审 1通过 2驳回 */
    private Integer auditStatus;

    private String auditRemark;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Integer viewCount;

    private Integer clickCount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
