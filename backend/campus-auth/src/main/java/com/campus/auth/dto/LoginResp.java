package com.campus.auth.dto;

import com.campus.common.client.dto.UserBrief;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResp {

    private String token;

    private UserBrief user;
}
