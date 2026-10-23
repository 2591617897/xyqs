package com.campus.user.dto;

import lombok.Data;

@Data
public class UpdateProfileReq {

    private String nickname;

    private String avatar;

    private Integer gender;

    private String phone;

    private String bio;
}
