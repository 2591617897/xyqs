package com.campus.common.client.dto;

import lombok.Data;

@Data
public class UserBrief {

    private Long id;

    private String nickname;

    private String avatar;

    private Long schoolId;

    private Integer verifyStatus;
}
