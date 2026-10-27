package com.campus.auth.service;

import com.campus.auth.mapper.WxOpenidMapper;
import com.campus.common.client.dto.StudentCheckVO;
import com.campus.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信学生身份核验（快速验证）。
 *
 * <p>链路：小程序授权插件返回 {@code wx_studentcheck_code} → 本服务带 openid 调
 * {@code /intp/quickcheckstudentidentity} → 得到 bind_status / is_student。
 *
 * <p><b>为什么放在 campus-auth</b>：appid/appsecret 与 access_token 缓存都在这里
 * （见 {@link WxSecurityService#accessToken(boolean)}）。业务服务拿不到凭据，
 * 也不该为此多持一份 token 缓存。
 *
 * <p><b>传输层坑</b>：和 msg_sec_check 一样，必须用 JDK {@link HttpClient} +
 * {@link HttpRequest.BodyPublishers#ofString}。Spring 的请求工厂会把 POST 退化成 chunked，
 * 微信对 chunked 直接回 HTTP 412 且无响应体，然后被上层当成「系统错误」吞掉。
 *
 * <p><b>隐私</b>：code 两小时有效且一次性，这里只做转发，不落库、不打日志；
 * 返回结果也只透传「是否学生」，姓名/学号/学校微信都不给。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxStudentService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CHECK_URL =
            "https://api.weixin.qq.com/intp/quickcheckstudentidentity?access_token=%s";

    /** 令牌失效：刷新后重试一次，与内容安全一致 */
    private static final int ERR_TOKEN_INVALID = 40001;
    private static final int ERR_TOKEN_EXPIRED = 42001;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final WxSecurityService wxSecurityService;
    private final WxOpenidMapper wxOpenidMapper;

    /**
     * 能力总开关。小程序后台「功能 → 学生验证」审核通过、且「设置 → 插件管理」添加了
     * 「微信学生身份验证」插件之后才能置 true，否则调用必回 48001。
     */
    @Value("${campus.wx.student-verify.enabled:false}")
    private boolean enabled;

    /**
     * 本地 mock：不调微信，直接返回「已绑定 + 是学生」。
     * 仅用于还没拿到能力授权时联调页面与落库，<b>生产必须 false</b>
     * （{{@code security-baseline-check.js}} 会扫这个开关）。
     */
    @Value("${campus.wx.student-verify.mock:false}")
    private boolean mock;

    /**
     * 核验学生身份。
     *
     * <p>所有「不可用 / 不通过」都以 {@link BizException} 抛出，message 是给用户看的话术
     * ——campus-user 会把 message 原样透传给小程序，不要在那边再包一层「认证失败」。
     *
     * @param userId 当前登录用户；用它的 openid 换结果（微信要求 code 与 openid 匹配）
     * @param code   插件回调里的 wx_studentcheck_code
     */
    public StudentCheckVO check(Long userId, String code) {
        if (!enabled) {
            throw new BizException("微信学生认证暂未开通，请改用学生证认证");
        }
        if (!StringUtils.hasText(code)) {
            throw new BizException("未获取到认证凭证，请重新发起微信认证");
        }
        String openid = wxOpenidMapper.selectOpenid(userId);
        if (!StringUtils.hasText(openid)) {
            throw new BizException("当前账号不是微信登录，无法使用微信学生认证");
        }
        if (mock) {
            log.warn("[wx-student] mock 已开启，userId={} 直接返回认证通过（生产必须关闭）", userId);
            return new StudentCheckVO(0, "ok", 3, true, true);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("openid", openid);
        body.put("wx_studentcheck_code", code);

        String token = wxSecurityService.accessToken(false);
        if (!StringUtils.hasText(token)) {
            throw new BizException("微信服务暂时不可用，请稍后再试或改用学生证认证");
        }
        JsonNode resp = post(CHECK_URL, token, body);
        int errcode = resp.path("errcode").asInt(0);
        if (errcode == ERR_TOKEN_INVALID || errcode == ERR_TOKEN_EXPIRED) {
            token = wxSecurityService.accessToken(true);
            if (!StringUtils.hasText(token)) {
                throw new BizException("微信服务暂时不可用，请稍后再试或改用学生证认证");
            }
            resp = post(CHECK_URL, token, body);
            errcode = resp.path("errcode").asInt(0);
        }

        Integer bindStatus = resp.hasNonNull("bind_status") ? resp.path("bind_status").asInt() : null;
        Boolean isStudent = resp.hasNonNull("is_student") ? resp.path("is_student").asBoolean() : null;
        String errmsg = resp.path("errmsg").asText("");

        if (errcode != 0) {
            // 这些错误对用户没意义，转成可操作的提示；code 过期要引导重新授权
            throw new BizException(describe(errcode, errmsg));
        }
        log.info("[wx-student] userId={} 核验完成 bindStatus={} isStudent={}", userId, bindStatus, isStudent);
        return new StudentCheckVO(errcode, errmsg, bindStatus, isStudent, false);
    }

    /**
     * 微信错误码 → 用户能看懂的一句话。
     *
     * <p>保留 raw 错误码在服务端日志里，前端只看到话术，避免把「internal error」这类
     * 原文抛给用户，也避免审核时被当成「接口异常未处理」。
     */
    private String describe(int errcode, String errmsg) {
        log.warn("[wx-student] 核验失败 errcode={} errmsg={}", errcode, errmsg);
        return switch (errcode) {
            case 40097 -> "认证信息与当前微信账号不匹配，请重新发起认证";
            case 83400 -> "本次认证已超时，请重新点击微信认证";
            case 83401 -> "今天查询学生身份的次数已达上限，请明天再试或改用学生证认证";
            case 83402 -> "认证场景不匹配（需快速验证），请升级微信后重试";
            case 48001, 61007 -> "小程序尚未开通微信学生认证能力，请改用学生证认证";
            case -1 -> "微信侧系统繁忙，请稍后再试";
            default -> "微信学生认证未通过，请稍后再试或改用学生证认证";
        };
    }

    /** 调一次核验接口；网络/解析异常统一转成用户可读话术 */
    private JsonNode post(String urlTemplate, String token, Map<String, Object> body) {
        String url = String.format(urlTemplate, token);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    // ofString 走固定长度，避免 chunked（微信对 chunked POST 回 412）
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200 || !StringUtils.hasText(resp.body())) {
                log.error("[wx-student] 接口返回异常 HTTP {} body={}", resp.statusCode(), resp.body());
                throw new BizException("微信服务暂时不可用，请稍后再试或改用学生证认证");
            }
            return JSON.readTree(resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[wx-student] 调用异常 {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new BizException("微信服务暂时不可用，请稍后再试或改用学生证认证");
        }
    }
}
