package com.campus.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.client.UserClient;
import com.campus.common.client.dto.LoginOrCreateReq;
import com.campus.common.client.dto.UserBrief;
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
    private String appid;

    @Value("${campus.qq.appsecret:}")
    private String appsecret;

    @Value("${campus.qq.mock:true}")
    private boolean mock;

    public LoginResp login(LoginReq req) {
        if (!StringUtils.hasText(req.getCode()) && !StringUtils.hasText(req.getDevOpenid())) {
            throw new BizException("缺少登录凭证 code");
        }
        QqSession session = code2Session(req.getCode(), req.getDevOpenid());

        LoginOrCreateReq createReq = new LoginOrCreateReq();
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
     * QQ 小程序 code2Session。未配置 appid 时走 mock，方便本地联调。
     */
    private QqSession code2Session(String code, String devOpenid) {
        if (mock || !StringUtils.hasText(appid)) {
            String openid = StringUtils.hasText(devOpenid)
                    ? devOpenid
                    : "mock_" + Math.abs(String.valueOf(code).hashCode());
            log.info("[mock] code2Session -> openid={}", openid);
            return new QqSession(openid, null);
        }
        String url = String.format(
                "https://api.q.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appid, appsecret, code);
        Map<?, ?> body = restTemplate.getForObject(url, Map.class);
        if (body == null || body.get("openid") == null) {
            Object errmsg = body == null ? null : body.get("errmsg");
            log.warn("code2Session 失败: {}", body);
            throw new BizException("微信/QQ 登录失败：" + (errmsg == null ? "未知错误" : errmsg));
        }
        return new QqSession(String.valueOf(body.get("openid")),
                body.get("unionid") == null ? null : String.valueOf(body.get("unionid")));
    }

    private record QqSession(String openid, String unionid) {
    }
}
