package com.campus.user.dto;

import lombok.Data;

/**
 * 公开主页 VO（GET /user/{userId}/profile）。
 * 只含对外可见字段，不返回 phone / studentNo / realName / verifyImg 等敏感信息。
 * id / schoolId 为 Long，经全局 JacksonConfig 序列化为字符串；计数为 Integer，保持数字。
 */
@Data
public class ProfileVO {

    private Long id;

    private String nickname;

    private String avatar;

    private String bio;

    private Long schoolId;

    private String schoolName;

    /** 0未认证 1审核中 2已认证 3驳回 */
    private Integer verifyStatus;

    /** 粉丝数（t_user 计数器） */
    private Integer fansCount;

    /** 关注数（t_user 计数器） */
    private Integer followCount;

    /** 当前登录用户是否已关注；未登录 / 本人 = false */
    private Boolean following;

    /** 对方是否关注我；未登录 / 本人 / 对方没关注 = false。与 following 同为 true 即互相关注 */
    private Boolean followedBy;

    /** 是否本人主页 */
    private Boolean isSelf;

    /** 闲置在售数（Feign 聚合 campus-goods，失败降级 0） */
    private Integer goodsSaleCount;

    /** 闲置累计发布数（Feign 聚合 campus-goods，失败降级 0） */
    private Integer goodsTotalCount;

    /** 帖子数（Feign 聚合 campus-post，失败降级 0） */
    private Integer postCount;
}
