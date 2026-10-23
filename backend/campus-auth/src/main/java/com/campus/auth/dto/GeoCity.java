package com.campus.auth.dto;

import lombok.Data;

/**
 * 位置信息 API（逆地理编码）解析出的城市信息。
 * available=false 表示 key 未配置或解析失败，前端回退到「学校所在市」。
 */
@Data
public class GeoCity {

    private String province;

    private String city;

    private String district;

    private boolean available;
}
