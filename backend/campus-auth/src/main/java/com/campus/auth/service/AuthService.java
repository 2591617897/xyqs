package com.campus.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.client.UserClient;
import com.campus.common.client.dto.LoginOrCreateReq;
import com.campus.common.client.dto.UserBrief;
import com.campus.auth.dto.GeoCity;
import com.campus.auth.dto.LoginReq;
import com.campus.auth.dto.LoginResp;
import com.campus.auth.entity.School;
import com.campus.auth.mapper.SchoolMapper;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SchoolMapper schoolMapper;
    private final UserClient userClient;
    private final JwtService jwtService;
    private final RestTemplate restTemplate;

    @Value("${campus.qq.appid:}")
    private String qqAppid;

    @Value("${campus.qq.appsecret:}")
    private String qqAppsecret;

    @Value("${campus.wx.appid:}")
    private String wxAppid;

    @Value("${campus.wx.appsecret:}")
    private String wxAppsecret;

    @Value("${campus.auth.mock:true}")
    private boolean mock;

    /** 腾讯位置服务 key（逆地理编码用），未配置时位置解析优雅降级 */
    @Value("${campus.geo.qqmap-key:}")
    private String qqmapKey;

    public LoginResp login(LoginReq req) {
        if (!StringUtils.hasText(req.getCode()) && !StringUtils.hasText(req.getDevOpenid())) {
            throw new BizException("缺少登录凭证 code");
        }
        String platform = normalizePlatform(req.getPlatform());
        Session session = code2Session(platform, req.getCode(), req.getDevOpenid());

        LoginOrCreateReq createReq = new LoginOrCreateReq();
        createReq.setPlatform(platform);
        createReq.setOpenid(session.openid());
        createReq.setUnionid(session.unionid());
        createReq.setNickname(req.getNickname());
        createReq.setAvatar(req.getAvatar());

        R<UserBrief> result = userClient.loginOrCreate(createReq);
        if (result == null || result.getCode() != 0 || result.getData() == null) {
            throw new BizException("登录失败，请稍后重试");
        }
        UserBrief user = result.getData();
        String token = jwtService.create(user.getId(), user.getSchoolId(), user.getVerifyStatus());
        return new LoginResp(token, user);
    }

    public List<School> schools(String keyword) {
        return schoolMapper.selectList(Wrappers.<School>lambdaQuery()
                .like(StringUtils.hasText(keyword), School::getName, keyword)
                .orderByDesc(School::getSort)
                .last("limit 50"));
    }

    /**
     * 位置信息 API：经纬度 → 省/市/区（腾讯位置服务 WebService 逆地理编码）。
     * key 未配置或调用失败时返回 available=false，前端据此回退到「学校所在市」。
     */
    public GeoCity resolveCity(double lat, double lng) {
        GeoCity resp = new GeoCity();
        resp.setAvailable(false);
        if (!StringUtils.hasText(qqmapKey)) {
            log.info("campus.geo.qqmap-key 未配置，逆地理编码降级为不可用");
            return resp;
        }
        try {
            String url = String.format(Locale.ROOT,
                    "https://apis.map.qq.com/ws/geocoder/v1/?location=%s,%s&key=%s&output=json&get_poi=0",
                    lat, lng, qqmapKey);
            Map<?, ?> body = restTemplate.getForObject(url, Map.class);
            Object result = body == null ? null : body.get("result");
            Object comp = result instanceof Map ? ((Map<?, ?>) result).get("address_component") : null;
            if (comp instanceof Map) {
                Map<?, ?> c = (Map<?, ?>) comp;
                resp.setProvince(asText(c.get("province")));
                resp.setCity(asText(c.get("city")));
                resp.setDistrict(asText(c.get("district")));
                resp.setAvailable(StringUtils.hasText(resp.getCity()));
            } else {
                log.warn("逆地理编码返回异常 body={}", body);
            }
        } catch (Exception e) {
            log.warn("逆地理编码失败 lat={} lng={} {}", lat, lng, e.getMessage());
        }
        return resp;
    }

    private String asText(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 未指定平台时按微信小程序处理（当前主端） */
    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return LoginOrCreateReq.DEFAULT_PLATFORM;
        }
        String p = platform.trim().toLowerCase(Locale.ROOT);
        return LoginOrCreateReq.DEFAULT_PLATFORM.equals(p) ? "wx" : "qq";
    }

    /**
     * 小程序 code2Session。微信与 QQ 的 URL/参数名一致，只有域名不同；
     * 微信额外返回 session_key（当前业务用不上，保留字段）。
     * 未配置对应平台 appid 时走 mock，方便本地联调。
     */
    private Session code2Session(String platform, String code, String devOpenid) {
        boolean qq = "qq".equals(platform);
        String appid = qq ? qqAppid : wxAppid;
        String appsecret = qq ? qqAppsecret : wxAppsecret;

        if (mock || !StringUtils.hasText(appid)) {
            String openid = StringUtils.hasText(devOpenid)
                    ? devOpenid
                    : "mock_" + platform + "_" + Math.abs(String.valueOf(code).hashCode());
            log.info("[mock] code2Session platform={} -> openid={}", platform, openid);
            return new Session(openid, null);
        }

        String url = qq
                ? String.format(
                "https://api.q.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appid, appsecret, code)
                : String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appid, appsecret, code);

        Map<?, ?> body = restTemplate.getForObject(url, Map.class);
        if (body == null || body.get("openid") == null) {
            Object errmsg = body == null ? null : body.get("errmsg");
            log.warn("code2Session 失败 platform={} body={}", platform, body);
            throw new BizException((qq ? "QQ" : "微信") + "登录失败：" + (errmsg == null ? "未知错误" : errmsg));
        }
        return new Session(String.valueOf(body.get("openid")),
                body.get("unionid") == null ? null : String.valueOf(body.get("unionid")));
    }

    private record Session(String openid, String unionid) {
    }
}
