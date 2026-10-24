package com.campus.user.dto;

import lombok.Data;

@Data
public class UpdateProfileReq {

    private String nickname;

    private String avatar;

    private Integer gender;

    private String phone;

    private String bio;

    /**
     * 学校 id。仅供「新用户引导选学校」使用：未完成学籍认证时可写入，
     * 已认证（verifyStatus=2）的用户禁止通过这里改校，必须重新走 /user/verify，
     * 否则等于绕过学生证审核。
     */
    private Long schoolId;
}
