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

    /** 脱敏：林* */
    private String realName;

    private Integer verifyStatus;

    private String verifyRemark;

    private String verifyImg;

    /** 粉丝数（「我的」页展示，Integer 保持数字） */
    private Integer fansCount;

    /** 关注数（「我的」页展示，Integer 保持数字） */
    private Integer followCount;

    private LocalDateTime createdAt;
}
