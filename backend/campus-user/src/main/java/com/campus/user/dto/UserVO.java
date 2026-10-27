package com.campus.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外返回的用户资料，敏感字段做脱敏处理。
 */
@Data
public class UserVO {

    private Long id;

    private String nickname;

    private String avatar;

    private Integer gender;

    private String phone;

    private String bio;

    private Long schoolId;

    private String schoolName;

    /** 学校所属省份 */
    private String schoolProvince;

    /** 学校所属城市（市域帖子流的聚合维度） */
    private String schoolCity;

    /** 学校所属区县 */
    private String schoolDistrict;

    /** 脱敏：2022****0101 */
    private String studentNo;

    /**
     * 注意：UserVO 绝不携带真实姓名。
     * 真实姓名仅在校验提交（VerifyReq）与后台人工审核（admin 服务）中出现，
     * 对外展示统一使用 nickname（即「我的」里设置的昵称），避免真实姓名泄漏。
     */
    private Integer verifyStatus;

    /** 认证来源 0未认证 1学生证材料 2微信学生认证（前端展示「微信认证」徽标用） */
    private Integer verifySource;

    /** 微信学生身份是否已核验通过 */
    private Integer wxStudentVerified;

    private String verifyRemark;

    private String verifyImg;

    /** 粉丝数（「我的」页展示，Integer 保持数字） */
    private Integer fansCount;

    /** 关注数（「我的」页展示，Integer 保持数字） */
    private Integer followCount;

    /** ===== 以下为「我的」页跨服务聚合统计，均由 UserService.me 通过 Feign 汇总 ===== */

    /** 我发布的商品总数（全部状态，不包括帖子） */
    private Integer goodsCount;

    /** 已卖出（status=2） */
    private Integer soldCount;

    /** 在售闲置（status=1） */
    private Integer activeCount;

    /** 帖子数（仅统计帖子，不含表白墙） */
    private Integer postCount;

    /**
     * 我的动态（帖子 + 表白墙，与「我的动态」页的「全部动态」同一口径）。
     * 只拿 postCount 会导致两处数字对不上。
     */
    private Integer dynamicCount;

    /** 赞与收藏（商品收藏数） */
    private Integer favoriteCount;

    /** 我买到的（买家订单数） */
    private Integer buyOrderCount;

    /** 我卖出的（卖家订单数） */
    private Integer sellOrderCount;

    /** 意向单（待确认的购买意向数） */
    private Integer intentCount;

    /** 发起的表白（表白墙 love 分类帖子数） */
    private Integer confessionCount;

    /** 树洞秘语（表白墙 tree 分类帖子数） */
    private Integer treeholeCount;

    /** 校园信用分（基于认证与活跃度的简单计算） */
    private Integer creditScore;

    private LocalDateTime createdAt;
}
