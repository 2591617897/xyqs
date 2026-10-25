package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告位。按校区/地区维度维护，小程序按 code 拉取。
 *
 * <p>audit_status 与 status 分离：先过审（1通过）才能上架（status=1），
 * 两个状态都由后台人工推进，避免运营误把未审素材推上线。
 */
@Data
@TableName("t_ad_slot")
public class AdSlot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String code;

    /** 1首页Banner 2频道页 3表白墙 4商品列表 5开屏 6个人中心 */
    private Integer position;

    /** 1全国 2城市 3校区 */
    private Integer scopeType;

    private String city;

    private Long schoolId;

    private String sizeDesc;

    private Integer maxCount;

    private Integer sort;

    /** 0下架 1上架 */
    private Integer status;

    /** 0待审 1通过 2驳回 */
    private Integer auditStatus;

    private String auditRemark;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
