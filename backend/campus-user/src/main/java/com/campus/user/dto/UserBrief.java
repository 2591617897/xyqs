package com.campus.user.dto;

import lombok.Data;

@Data
public class UserBrief {

    private Long id;

    private String nickname;

    private String avatar;

    private Long schoolId;

    private Integer verifyStatus;
}
