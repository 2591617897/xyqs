package com.campus.auth.service;

import com.campus.auth.mapper.WxOpenidMapper;
import com.campus.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信内容安全（msg_sec_check v2）。
 *
 * 放在 campus-auth 的原因：appid / appsecret 与全仓唯一的 RestTemplate 都在本模块，
 * 业务服务只需要通过 {@code SecurityClient} 发一行 Feign 调用。
 *
 * 设计取舍：
 * <ul>
 *   <li>access_token 用<b>进程内缓存</b>（7200s，提前 300s 过期）。全仓没有 Redis，
 *       这是项目既定做法（见 campus-admin 的 AdminAuthInterceptor）。多实例部署时
 *       每个实例各持一份 token，微信侧允许，只是刷新次数变多。</li>
 *   <li><b>默认关闭</b>（campus.wx.sec-check.enabled=false）。未配 appsecret 时不报错、
 *       不影响发帖，避免本地与 CI 被外部依赖卡住。</li>
 *   <li>微信侧异常（网络、配额、未知 errcode）一律 <b>放行</b>（fail-open）并打日志：
 *       审核是增强能力，不能变成全站发不出内容的单点故障。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxSecurityService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    private static final String MSG_CHECK_URL =
            "https://api.weixin.qq.com/wxa/msg_sec_check?access_token=%s";

    /** 命中违规内容 */
    private static final int ERR_RISKY = 87014;
    /** access_token 非法 / 过期，需要刷新后重试一次 */
    private static final int ERR_TOKEN_INVALID = 40001;
    private static final int ERR_TOKEN_EXPIRED = 42001;

    /** 提前 300s 视为过期，避开边界失效 */
    private static final long TOKEN_SAFETY_MS = 300_000L;

    /** msg_sec_check 单次内容上限 2500 字，留出余量 */
    private static final int MAX_CONTENT_LEN = 2000;

    private final RestTemplate restTemplate;
    private final WxOpenidMapper wxOpenidMapper;

    @Value("${campus.wx.appid:}")
    private String appid;

    @Value("${campus.wx.appsecret:}")
    private String appsecret;

    @Value("${campus.wx.sec-check.enabled:false}")
    private boolean enabled;

    private volatile String cachedToken;

    private volatile long tokenExpireAt;

    /** 开关打开且凭据齐备时才真正生效 */
    public boolean active() {
        return enabled && StringUtils.hasText(appid) && StringUtils.hasText(appsecret);
    }

    /**
     * 校验一段用户提交的文本。
     *
     * @param userId  提交者 id（用于反查 openid）
     * @param content 待校验文本
     * @param scene   微信场景值：1 资料 / 2 评论 / 3 论坛 / 4 社交日志
     * @throws BizException 命中违规时抛 400，由 GlobalExceptionHandler 转成统一响应
     */
    public void checkText(Long userId, String content, Integer scene) {
        if (!active() || !StringUtils.hasText(content)) {
            return;
        }
        String openid = userId == null ? null : wxOpenidMapper.selectOpenid(userId);
        if (!StringUtils.hasText(openid)) {
            log.warn("[sec-check] userId={} 未取到 openid，跳过内容审核", userId);
            return;
        }

        String token = accessToken(false);
        if (token == null) {
            log.warn("[sec-check] 未取到 access_token，本条放行");
            return;
        }

        Map<String, Object> body = new HashMap<>(4);
        body.put("content", content.length() > MAX_CONTENT_LEN
                ? content.substring(0, MAX_CONTENT_LEN) : content);
        body.put("version", 2);
        body.put("scene", scene == null ? 3 : scene);
        body.put("openid", openid);

        JsonNode resp = postCheck(token, body);
        int errcode = resp == null ? -1 : resp.path("errcode").asInt(0);

        if (errcode == ERR_TOKEN_INVALID || errcode == ERR_TOKEN_EXPIRED) {
            log.info("[sec-check] access_token 失效（errcode={}），刷新后重试一次", errcode);
            invalidateToken();
            token = accessToken(true);
            if (token == null) {
                return;
            }
            resp = postCheck(token, body);
            errcode = resp == null ? -1 : resp.path("errcode").asInt(0);
        }

        if (errcode == ERR_RISKY) {
            throw BizException.of("内容涉嫌违规，请修改后再发布");
        }
        if (resp == null || errcode != 0) {
            log.warn("[sec-check] 微信返回异常 errcode={} errmsg={}，本条放行",
                    errcode, resp == null ? "" : resp.path("errmsg").asText(""));
            return;
        }

        // v2 返回 result.suggest：pass / review / risky
        String suggest = resp.path("result").path("suggest").asText("");
        if ("risky".equalsIgnoreCase(suggest)) {
            throw BizException.of("内容涉嫌违规，请修改后再发布");
        }
        if ("review".equalsIgnoreCase(suggest)) {
            log.info("[sec-check] 内容命中疑似风险，已放行待人工复核 label={}",
                    resp.path("result").path("label").asInt(0));
        }
    }

    /** 调微信校验接口；任何异常返回 null（由调用方按 fail-open 处理） */
    private JsonNode postCheck(String token, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String raw = restTemplate.postForObject(
                    String.format(MSG_CHECK_URL, token),
                    new HttpEntity<>(body, headers),
                    String.class);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return JSON.readTree(raw);
        } catch (Exception e) {
            log.warn("[sec-check] 调用微信内容安全接口失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 取 access_token（进程内缓存）。
     *
     * @param forceRefresh true 时忽略缓存强制刷新
     */
    private synchronized String accessToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedToken != null && now < tokenExpireAt) {
            return cachedToken;
        }
        try {
            String raw = restTemplate.getForObject(
                    String.format(TOKEN_URL, appid, appsecret), String.class);
            if (!StringUtils.hasText(raw)) {
                log.error("[sec-check] 获取 access_token 返回空");
                return null;
            }
            JsonNode node = JSON.readTree(raw);
            String token = node.path("access_token").asText("");
            if (!StringUtils.hasText(token)) {
                // 常见：40125 appsecret 错误 / 40164 IP 不在白名单
                log.error("[sec-check] 获取 access_token 失败：errcode={} errmsg={}",
                        node.path("errcode").asInt(-1), node.path("errmsg").asText(""));
                return null;
            }
            cachedToken = token;
            long expiresIn = node.path("expires_in").asLong(7200L);
            tokenExpireAt = now + expiresIn * 1000L - TOKEN_SAFETY_MS;
            log.info("[sec-check] access_token 已刷新，{}s 后过期", expiresIn);
            return token;
        } catch (Exception e) {
            log.error("[sec-check] 获取 access_token 异常：{}", e.getMessage());
            return null;
        }
    }

    private synchronized void invalidateToken() {
        cachedToken = null;
        tokenExpireAt = 0L;
    }
}
