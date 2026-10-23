package com.campus.auth.controller;

import com.campus.auth.dto.GeoCity;
import com.campus.auth.dto.LoginReq;
import com.campus.auth.dto.LoginResp;
import com.campus.auth.entity.School;
import com.campus.auth.service.AuthService;
import com.campus.common.api.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** qq.login() 拿到 code 后换取 token */
    @PostMapping("/auth/login")
    public R<LoginResp> login(@RequestBody LoginReq req) {
        return R.ok(authService.login(req));
    }

    /** 学校字典，注册时必选 */
    @GetMapping("/schools")
    public R<List<School>> schools(@RequestParam(required = false, name = "keyword") String keyword) {
        return R.ok(authService.schools(keyword));
    }

    /**
     * 位置信息 API：经纬度 → 所在城市（逆地理编码）。
     * key 未配置或解析失败时 available=false，前端回退到学校所在市。
     */
    @GetMapping("/auth/geo/city")
    public R<GeoCity> geoCity(@RequestParam(required = false, name = "lat") Double lat,
                              @RequestParam(required = false, name = "lng") Double lng) {
        if (lat == null || lng == null) {
            return R.ok(new GeoCity());
        }
        return R.ok(authService.resolveCity(lat, lng));
    }
}
