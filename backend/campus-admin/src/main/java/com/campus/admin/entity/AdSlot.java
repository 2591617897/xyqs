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

    /**
     * 投放城市，<b>逗号分隔多值</b>（如 {@code 贵阳,遵义}）。
     *
     * <p>必须与 t_school.city 逐字一致（不带「市」字）：C 端拿用户学校所在城市做
     * {@code FIND_IN_SET} 匹配，差一个字就永不命中，且两端都不报错。
     * 后台已改成下拉多选，选项直接取自 t_school 的去重结果，杜绝手输。
     */
    private String city;

    /**
     * 投放校区 ID，<b>逗号分隔多值</b>（如 {@code 1001,1002}）。
     *
     * <p>原来是单值 bigint，只能定向一所学校；运营想「贵阳三所高校同投」
     * 就必须建三个广告位，而 code 是唯一的根本建不出来。
     * 换成多值后一个广告位就能覆盖多个校区。
     */
    private String schoolIds;

    /**
     * 展示规格编码，决定小程序渲染高度（{@code com.campus.common.ad.AdSizeCatalog}）。
     *
     * <p>只能是规格表里已有的编码：小程序按它撑容器，后台按它校验素材比例。
     * 为空时视为「按该广告位的推荐规格」（老数据 / 手工插库），读取时兜底成
     * {@code AdSizeCatalog.DEFAULT_CODE}，不会渲染成 0 高度。
     */
    private String sizeCode;

    /**
     * 尺寸说明文案，如 {@code 750×240 · 3.13:1 · 建议 1080×346}。
     *
     * <p><b>由 size_code 派生，不接受手填</b>（服务端保存时覆盖）。保留这一列只为
     * 列表展示与历史数据兼容——早期它是自由文本框，运营填过「800x600」这种
     * 与实际渲染无关的值，是「后台说 A、真机渲染 B」的源头。
     */
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
