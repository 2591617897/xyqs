package com.campus.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.auth.entity.ContentAuditTrail;
import com.campus.auth.entity.MediaCheck;
import com.campus.auth.mapper.MediaCheckMapper;
import com.campus.auth.mapper.WxOpenidMapper;
import com.campus.common.client.dto.MediaCheckReq;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信内容安全：文本同步审核（msg_sec_check v2）+ 图片异步审核（media_check_async v2）。
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
 *
 * <p>文本与图片的差别：文本同步出结论，命中就可以在落库前拦掉（REJECT）；
 * 图片是<b>先发后审</b>——media_check_async 只返回 trace_id，结论由
 * {@link #handleCallback} 在微信回调时落库，命中违规后由运营在后台按台账处置。
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
    private static final String MEDIA_CHECK_URL =
            "https://api.weixin.qq.com/wxa/media_check_async?access_token=%s";

    /** 命中违规内容 */
    private static final int ERR_RISKY = 87014;
    /** access_token 非法 / 过期，需要刷新后重试一次 */
    private static final int ERR_TOKEN_INVALID = 40001;
    private static final int ERR_TOKEN_EXPIRED = 42001;

    /** 提前 300s 视为过期，避开边界失效 */
    private static final long TOKEN_SAFETY_MS = 300_000L;

    /** msg_sec_check 单次内容上限 2500 字，留出余量 */
    private static final int MAX_CONTENT_LEN = 2000;
    /** 违规摘要落库长度，与 t_content_audit.content_brief(500) 匹配 */
    private static final int BRIEF_LEN = 200;
    /** 平台普通图片上限 10MB（微信 media_check_async 要求 <10MB） */

    /** 台账状态：0 待审 / 1 通过 / 2 疑似 / 3 违规 / 4 送审失败 */
    private static final int ST_PENDING = 0;
    private static final int ST_PASS = 1;
    private static final int ST_REVIEW = 2;
    private static final int ST_RISKY = 3;
    private static final int ST_FAIL = 4;

    /** 处置动作：拒绝发布 / 疑似告警 */
    private static final String ACTION_REJECT = "REJECT";
    private static final String ACTION_WARN = "WARN";
    /** 来源：2 = 系统自动（与后台人工=1、举报=3 区分） */
    private static final int SOURCE_SYSTEM = 2;

    private final RestTemplate restTemplate;
    private final WxOpenidMapper wxOpenidMapper;
    private final MediaCheckMapper mediaCheckMapper;
    private final ContentAuditTrailWriter auditWriter;

    @Value("${campus.wx.appid:}")
    private String appid;

    @Value("${campus.wx.appsecret:}")
    private String appsecret;

    @Value("${campus.wx.sec-check.enabled:false}")
    private boolean enabled;

    /** 图片异步审核单独开关：默认跟随总开关，可单独关掉（例如本地没有公网图片时） */
    @Value("${campus.wx.sec-check.media-enabled:true}")
    private boolean mediaEnabled;

    /** 微信「消息推送」里填的 Token，用于回调验签 */
    @Value("${campus.wx.callback-token:}")
    private String callbackToken;

    private volatile String cachedToken;

    private volatile long tokenExpireAt;

    /** 开关打开且凭据齐备时才真正生效 */
    public boolean active() {
        return enabled && StringUtils.hasText(appid) && StringUtils.hasText(appsecret);
    }

    /** 图片审核是否可用 */
    public boolean mediaActive() {
        return active() && mediaEnabled;
    }

    /** 回调验签 Token 是否已配置（未配置时回调一律拒绝，防止裸奔写入台账） */
    public boolean callbackReady() {
        return StringUtils.hasText(callbackToken);
    }

    // ==================== 文本 ====================

    /**
     * 校验一段用户提交的文本。
     *
     * @param userId  提交者 id（用于反查 openid）
     * @param content 待校验文本
     * @param scene   微信场景值：1 资料 / 2 评论 / 3 论坛 / 4 社交日志
     * @param bizType 业务类型，仅用于违规事件落 t_content_audit
     * @throws BizException 命中违规时抛 400，由 GlobalExceptionHandler 转成统一响应
     */
    public void checkText(Long userId, String content, Integer scene, String bizType) {
        if (!active() || !StringUtils.hasText(content)) {
            return;
        }
        String openid = openidOf(userId);
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

        JsonNode resp = postJson(MSG_CHECK_URL, token, body);
        int errcode = resp == null ? -1 : resp.path("errcode").asInt(0);

        if (errcode == ERR_TOKEN_INVALID || errcode == ERR_TOKEN_EXPIRED) {
            log.info("[sec-check] access_token 失效（errcode={}），刷新后重试一次", errcode);
            invalidateToken();
            token = accessToken(true);
            if (token == null) {
                return;
            }
            resp = postJson(MSG_CHECK_URL, token, body);
            errcode = resp == null ? -1 : resp.path("errcode").asInt(0);
        }

        if (errcode == ERR_RISKY) {
            recordViolation(bizType, null, userId, content, ACTION_REJECT,
                    "微信内容安全判定违规（errcode=87014）");
            throw BizException.of("内容涉嫌违规，请修改后再发布");
        }
        if (resp == null || errcode != 0) {
            log.warn("[sec-check] 微信返回异常 errcode={} errmsg={}，本条放行",
                    errcode, resp == null ? "" : resp.path("errmsg").asText(""));
            return;
        }

        // v2 返回 result.suggest：pass / review / risky
        String suggest = resp.path("result").path("suggest").asText("");
        int label = resp.path("result").path("label").asInt(0);
        if ("risky".equalsIgnoreCase(suggest)) {
            recordViolation(bizType, null, userId, content, ACTION_REJECT,
                    "微信内容安全判定违规：suggest=risky label=" + label);
            throw BizException.of("内容涉嫌违规，请修改后再发布");
        }
        if ("review".equalsIgnoreCase(suggest)) {
            // 疑似：放行但留痕，方便运营事后复核与申诉举证
            log.info("[sec-check] 内容命中疑似风险，已放行待人工复核 label={}", label);
            recordViolation(bizType, null, userId, content, ACTION_WARN,
                    "微信内容安全判定疑似：suggest=review label=" + label);
        }
    }

    // ==================== 图片（异步） ====================

    /**
     * 提交图片异步审核。
     *
     * <p>微信 media_check_async 只接受公网可访问的 media_url（微信侧会去下载），
     * 所以 http://127.0.0.1、局域网 IP、临时文件路径都会被过滤掉 —— 这类地址在
     * 本地联调时提交必然失败，属预期行为。
     *
     * @return 实际提交成功的图片数
     */
    public int submitMediaCheck(MediaCheckReq req) {
        if (!mediaActive() || req == null || req.urls() == null || req.urls().isEmpty()) {
            return 0;
        }
        String openid = openidOf(req.userId());
        if (!StringUtils.hasText(openid)) {
            log.warn("[sec-media] userId={} 未取到 openid，跳过图片审核", req.userId());
            return 0;
        }
        String token = accessToken(false);
        if (token == null) {
            log.warn("[sec-media] 未取到 access_token，跳过图片审核");
            return 0;
        }

        int scene = "MESSAGE".equalsIgnoreCase(req.bizType()) ? 4 : 3;
        int mediaType = req.mediaType() == null ? 1 : req.mediaType();
        int submitted = 0;
        for (String url : req.urls()) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                log.debug("[sec-media] 跳过非公网图片：{}", url);
                continue;
            }
            Map<String, Object> body = new HashMap<>(5);
            body.put("media_url", url);
            body.put("media_type", mediaType);
            body.put("version", 2);
            body.put("scene", scene);
            body.put("openid", openid);

            JsonNode resp = postJson(MEDIA_CHECK_URL, token, body);
            int errcode = resp == null ? -1 : resp.path("errcode").asInt(0);
            if (errcode == ERR_TOKEN_INVALID || errcode == ERR_TOKEN_EXPIRED) {
                log.info("[sec-media] access_token 失效（errcode={}），刷新后重试一次", errcode);
                invalidateToken();
                token = accessToken(true);
                if (token == null) {
                    break;
                }
                resp = postJson(MEDIA_CHECK_URL, token, body);
                errcode = resp == null ? -1 : resp.path("errcode").asInt(0);
            }

            String traceId = resp == null ? "" : resp.path("trace_id").asText("");
            if (errcode != 0 || !StringUtils.hasText(traceId)) {
                log.warn("[sec-media] 送审失败 url={} errcode={} errmsg={}", url, errcode,
                        resp == null ? "no-response" : resp.path("errmsg").asText(""));
                saveMediaRow(req, url, mediaType, null, ST_FAIL, null, null, errcode);
                continue;
            }
            saveMediaRow(req, url, mediaType, traceId, ST_PENDING, null, null, 0);
            submitted++;
        }
        return submitted;
    }

    /**
     * 处理微信「消息推送」回调（Event = wxa_media_check）。
     *
     * <p>微信推的是 JSON，形如：
     * <pre>
     * { "MsgType":"event", "Event":"wxa_media_check", "trace_id":"...",
     *   "version":2, "errcode":0, "errmsg":"ok",
     *   "result":{"suggest":"risky","label":20002} }
     * </pre>
     * 命中 review / risky 时同时写一条处置流水（t_content_audit），源标记为「系统自动」。
     */
    public void handleCallback(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return;
        }
        JsonNode node;
        try {
            node = JSON.readTree(rawBody);
        } catch (Exception e) {
            log.warn("[sec-media] 回调报文不是 JSON（请在微信后台把消息推送数据格式设为 JSON）：{}",
                    abbreviate(rawBody));
            return;
        }
        if (!"wxa_media_check".equalsIgnoreCase(node.path("Event").asText(""))) {
            log.debug("[sec-media] 忽略非媒体审核事件：{}", node.path("Event").asText(""));
            return;
        }
        String traceId = node.path("trace_id").asText("");
        if (!StringUtils.hasText(traceId)) {
            log.warn("[sec-media] 回调缺少 trace_id：{}", abbreviate(rawBody));
            return;
        }
        MediaCheck mc = mediaCheckMapper.selectByTraceId(traceId);
        if (mc == null) {
            log.warn("[sec-media] 回调 trace_id={} 在台账中不存在（可能已被清理）", traceId);
            return;
        }

        int errcode = node.path("errcode").asInt(0);
        String suggest = node.path("result").path("suggest").asText("");
        Integer label = node.path("result").has("label")
                ? node.path("result").path("label").asInt(0) : null;

        int status;
        if (errcode != 0) {
            status = ST_FAIL;
        } else if ("risky".equalsIgnoreCase(suggest)) {
            status = ST_RISKY;
        } else if ("review".equalsIgnoreCase(suggest)) {
            status = ST_REVIEW;
        } else if ("pass".equalsIgnoreCase(suggest)) {
            status = ST_PASS;
        } else {
            status = ST_REVIEW;
        }

        mediaCheckMapper.update(null, Wrappers.<MediaCheck>lambdaUpdate()
                .eq(MediaCheck::getId, mc.getId())
                .set(MediaCheck::getStatus, status)
                .set(MediaCheck::getSuggest, suggest)
                .set(MediaCheck::getLabel, label)
                .set(MediaCheck::getErrcode, errcode)
                .set(MediaCheck::getUpdatedAt, LocalDateTime.now()));

        if (status == ST_RISKY || status == ST_REVIEW) {
            recordViolation(mc.getBizType(), mc.getBizId(), mc.getUserId(),
                    mc.getMediaUrl(),
                    status == ST_RISKY ? ACTION_REJECT : ACTION_WARN,
                    "图片审核：" + suggest + " label=" + (label == null ? 0 : label));
        }
        log.info("[sec-media] 回调处理完成 trace_id={} biz={}/{} status={} suggest={}",
                traceId, mc.getBizType(), mc.getBizId(), status, suggest);
    }

    // ==================== 回调验签 ====================

    /**
     * 微信消息推送签名校验：sha1(sort(token, timestamp, nonce) 拼成一串)。
     *
     * <p>只支持「明文模式 + JSON」：安全模式（AES 加密）需要额外处理
     * encodingAesKey 解密，本项目未启用，微信后台保持默认明文即可。
     */
    public boolean verifySignature(String signature, String timestamp, String nonce) {
        if (!callbackReady() || !StringUtils.hasText(signature)
                || !StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            return false;
        }
        String[] arr = {callbackToken, timestamp, nonce};
        Arrays.sort(arr);
        String expected = sha1(String.join("", arr));
        return expected.equalsIgnoreCase(signature);
    }

    private static String sha1(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 内部方法 ====================

    private String openidOf(Long userId) {
        return userId == null ? null : wxOpenidMapper.selectOpenid(userId);
    }

    private void saveMediaRow(MediaCheckReq req, String url, int mediaType, String traceId,
                              int status, String suggest, Integer label, int errcode) {
        try {
            MediaCheck mc = new MediaCheck();
            mc.setTraceId(StringUtils.hasText(traceId) ? traceId : "local-" + System.nanoTime());
            mc.setUserId(req.userId());
            mc.setBizType(req.bizType());
            mc.setBizId(req.bizId());
            mc.setMediaUrl(url.length() > 480 ? url.substring(0, 480) : url);
            mc.setMediaType(mediaType);
            mc.setStatus(status);
            mc.setSuggest(suggest);
            mc.setLabel(label);
            mc.setErrcode(errcode);
            mediaCheckMapper.insert(mc);
        } catch (Exception e) {
            // 台账写失败不能影响发布流程
            log.warn("[sec-media] 写审核台账失败：{}", e.getMessage());
        }
    }

    /**
     * 写一条「系统自动」处置流水。
     *
     * <p>统一走 {@link ContentAuditTrailWriter}，与本地敏感词共用的语义和入口，
     * 避免两处实现漂移。
     */
    private void recordViolation(String targetType, Long targetId, Long authorId, String content,
                                 String action, String reason) {
        auditWriter.record(targetType, targetId, authorId, content, action, reason);
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= BRIEF_LEN ? s : s.substring(0, BRIEF_LEN);
    }

    /** 调微信接口；任何异常返回 null（由调用方按 fail-open 处理） */
    private JsonNode postJson(String urlTemplate, String token, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String raw = restTemplate.postForObject(
                    String.format(urlTemplate, token),
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

    /** 供回调/内部接口判断是否要用：媒体送审失败时把原因带出去 */
    public List<String> describeStatus() {
        return List.of(
                "active=" + active(),
                "mediaActive=" + mediaActive(),
                "callbackReady=" + callbackReady());
    }
}
