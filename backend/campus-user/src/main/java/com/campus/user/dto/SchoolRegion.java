package com.campus.user.dto;

import lombok.Data;

/**
 * 学校所属区域（省/市/区县），来自 t_school。
 */
@Data
public class SchoolRegion {

    private String province;

    private String city;

    private String district;
}
