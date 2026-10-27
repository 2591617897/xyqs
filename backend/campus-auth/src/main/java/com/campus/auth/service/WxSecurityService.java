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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信内容安全：文本同步审核（msg_sec_check v2）+ 图片异步审核（media_check_async v2）。
 *
 * 放在 campus-auth 的原因：appid / appsecret 只在 auth 手里，
 * 业务服务只需要通过 {@code SecurityClient} 发一行 Feign 调用。
 *
 * <p><b>传输层注意</b>：这三个接口（token / msg_sec_check / media_check_async）都直接用
 * JDK {@link HttpClient}，不走模块里的 {@code RestTemplate}。原因见 {@code AuthConfig}
 * 的注释——Spring 的请求工厂会退化成 chunked，而微信对 chunked 的 POST 回 HTTP 412
 * （无响应体），且会被 fail-open 吞成「放行」，极难排查。
 *
 * 设计取舍：
 * <ul>
 *   <li>access_token 用<b>进程内缓存</b>（7200s，提前 300s 过期）。全仓没有 Redis，
 *       这是项目既定做法（见 campus-admin 的 AdminAuthInterceptor）。多实例部署时
 *       每个实例各持一份 token，微信侧允许，只是刷新次数变多。</li>
 *   <li><b>默认关闭</b>（campus.wx.sec-check.enabled=false）。未配 appsecret 时不报错、
 *       不影响发帖，避免本地与 CI 被外部依赖卡住。</li>
 *   <li>微信侧异常（网络、配额、未知 errcode）一律 <b>放行</b>（fail-open）并打日志：
 *       审核是增强能力，不能变成全站发不出内容的单点故障。副作用是「配置错了也不报错」，
 *       所以提供了 {@link #diagnose} 自检接口。</li>
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

    /**
     * 微信接口专用客户端。
     *
     * <p>用 JDK HttpClient 而不是模块里的 RestTemplate：Spring 的请求工厂在写请求体时
     * 长度未知 → 退化成 chunked → 微信 POST 接口回 <b>HTTP 412（无 body）</b>。
     * {@code BodyPublishers.ofString(...)} 一定带 Content-Length，实测 200。
     * 强制 HTTP/1.1：微信不支持 h2 升级。
     */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

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

    /** 调微信接口；任何异常 / 非 200 返回 null（由调用方按 fail-open 处理） */
    private JsonNode postJson(String urlTemplate, String token, Map<String, Object> body) {
        String url = String.format(urlTemplate, token);
        try {
            String json = JSON.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200 || !StringUtils.hasText(resp.body())) {
                // 412 不是 errcode：微信侧的连接层拒绝，多半是 chunked / 安全校验，
                // 这种情况下"失败"会表现为"全部放行"，必须显式打出来
                log.warn("[sec-check] 微信接口返回 HTTP {}（412 常见于 chunked 传输被拒）path={}",
                        resp.statusCode(), pathOnly(url));
                return null;
            }
            return JSON.readTree(resp.body());
        } catch (Exception e) {
            log.warn("[sec-check] 调用微信内容安全接口失败：{}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 取 access_token（进程内缓存）。
     *
     * <p><b>为什么是 public</b>：微信学生身份核验（{@code WxStudentService}）用的是同一个
     * appid/secret、同一份 token 配额。如果各写一套缓存，两个服务各自刷 token 会互相把对方的
     * 令牌顶掉（微信同一 appid 刷新会失效旧 token），线上表现为随机 40001。
     * 所以这里把方法开放出来复用，<b>不要</b>在别处再写一份 fetchToken。
     *
     * @param forceRefresh true 时忽略缓存强制刷新
     */
    public synchronized String accessToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedToken != null && now < tokenExpireAt) {
            return cachedToken;
        }
        JsonNode node = fetchToken();
        if (node == null) {
            return null;
        }
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
    }

    /** 拉一次 access_token 的原始响应（自检要看 errcode/errmsg，不能只拿 null） */
    private JsonNode fetchToken() {
        String url = String.format(TOKEN_URL, appid, appsecret);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (!StringUtils.hasText(resp.body())) {
                log.error("[sec-check] 获取 access_token 返回空，HTTP {}", resp.statusCode());
                return null;
            }
            return JSON.readTree(resp.body());
        } catch (Exception e) {
            log.error("[sec-check] 获取 access_token 异常：{}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /** 日志里只留路径，别把 access_token / secret 打出去 */
    private static String pathOnly(String url) {
        int q = url == null ? -1 : url.indexOf('?');
        return q > 0 ? url.substring(0, q) : url;
    }

    /** 把 access_token 接口的错误码翻译成「人话」，自检输出用 */
    private static String tokenHint(int errcode, String errmsg) {
        return switch (errcode) {
            case 0 -> "凭据正常";
            case 40013 -> "AppID 不合法：检查 WX_APPID 是否与小程序后台一致";
            case 40125 -> "AppSecret 错误：去公众平台重置后更新 WX_APPSECRET（改完需重启 auth）";
            case 40164 -> "调用 IP 不在白名单：公众平台 → 开发管理 → 开发设置 → IP 白名单，"
                    + "把本机/服务器的公网出口 IP 加进去（errmsg 里通常带被拒的 IP）";
            case 45009 -> "接口调用次数超限（每日 2000 次），明天重置或申请提额";
            case 45011 -> "频率限制，稍后重试";
            default -> "未知错误 errcode=" + errcode + " errmsg=" + errmsg;
        };
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

    // ==================== 自检 ====================

    /** 探测文本：这几类内容模型判定很稳定，能区分「真通了」和「碰巧 pass」 */
    private static final String PROBE_TEXT = "代开发票，需要的私聊";

    /**
     * 配置自检：一次回答四个问题 —— 开关开了吗、凭据对吗、IP 白名单过了吗、真能判出违规吗。
     *
     * <p>为什么需要它：msg_sec_check 是 <b>fail-open</b> 的，配置错了（secret 错、IP 没加白、
     * 开关忘开）表现都是「发帖一切正常」，只有翻日志才看得到 warn。上线前跑一次这个接口，
     * 就不用靠猜。
     *
     * <p>自检<b>不会</b>写 t_content_audit（用的是独立的探测请求，不经过 {@link #checkText}），
     * 可以随时重复调用；每次消耗 1 次 msg_sec_check 配额。
     *
     * @param userId    可选，指定用哪个用户的 openid 探测；不传则取库里最新的微信用户
     * @param probeText 可选，自定义探测文本；不传用内置的违规样本
     */
    public Map<String, Object> diagnose(Long userId, String probeText) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled);
        out.put("active", active());
        out.put("appid", mask(appid));
        out.put("secretConfigured", StringUtils.hasText(appsecret));
        out.put("mediaActive", mediaActive());
        out.put("callbackConfigured", callbackReady());

        List<String> hints = new ArrayList<>();
        if (!enabled) {
            hints.add("总开关关着：设环境变量 WX_SEC_CHECK=true 后重启 campus-auth");
        }
        if (!StringUtils.hasText(appid)) {
            hints.add("WX_APPID 为空，微信侧无法认定调用方");
        }
        if (!StringUtils.hasText(appsecret)) {
            hints.add("WX_APPSECRET 为空：appid 与 appsecret 必须同时在位，缺一个整条链路都不生效");
        }
        if (!callbackReady()) {
            hints.add("WX_CALLBACK_TOKEN 为空：图片审核结论回调会被拒绝（文本审核不受影响）");
        }

        Map<String, Object> tokenInfo = new LinkedHashMap<>();
        Map<String, Object> probeInfo = new LinkedHashMap<>();

        if (!active()) {
            tokenInfo.put("ok", false);
            tokenInfo.put("hint", "开关未开或凭据不全，未发起请求");
        } else {
            JsonNode node = fetchToken();
            int errcode = node == null ? -1 : node.path("errcode").asInt(0);
            String errmsg = node == null ? "no-response（网络不通或被墙）" : node.path("errmsg").asText("");
            String token = node == null ? "" : node.path("access_token").asText("");
            boolean tokenOk = StringUtils.hasText(token);
            tokenInfo.put("ok", tokenOk);
            tokenInfo.put("errcode", errcode);
            tokenInfo.put("errmsg", errmsg);
            tokenInfo.put("hint", tokenHint(errcode, errmsg));
            if (!tokenOk) {
                hints.add("access_token 拿不到 → " + tokenHint(errcode, errmsg));
            } else {
                // token 有效就顺手刷进缓存，省得业务请求再拉一次
                synchronized (this) {
                    cachedToken = token;
                    long expiresIn = node.path("expires_in").asLong(7200L);
                    tokenExpireAt = System.currentTimeMillis() + expiresIn * 1000L - TOKEN_SAFETY_MS;
                }
                // 用 int 而不是 long：项目全局把 Long 序列化成字符串，int 才保持数字
                tokenInfo.put("expiresIn", node.path("expires_in").asInt(7200));
                probe(token, userId, probeText, probeInfo, hints);
            }
        }

        out.put("token", tokenInfo);
        out.put("probe", probeInfo);
        out.put("hints", hints);
        out.put("ready", Boolean.TRUE.equals(tokenInfo.get("ok")) && Boolean.TRUE.equals(probeInfo.get("ok")));
        return out;
    }

    /** 真发一次 msg_sec_check，看是不是能判出违规 */
    private void probe(String token, Long userId, String probeText,
                       Map<String, Object> probeInfo, List<String> hints) {
        String openid = openidOf(userId);
        if (!StringUtils.hasText(openid)) {
            openid = wxOpenidMapper.selectAnyOpenid();
        }
        if (!StringUtils.hasText(openid)) {
            probeInfo.put("ok", false);
            probeInfo.put("hint", "库里没有可用的微信 openid，msg_sec_check v2 必须带 openid，无法探测");
            hints.add("t_user 里没有 platform='wx' 的 openid：先在小程序里登录一次再自检");
            return;
        }
        String text = StringUtils.hasText(probeText) ? probeText : PROBE_TEXT;
        Map<String, Object> body = new HashMap<>(4);
        body.put("content", text.length() > MAX_CONTENT_LEN ? text.substring(0, MAX_CONTENT_LEN) : text);
        body.put("version", 2);
        body.put("scene", 3);
        body.put("openid", openid);

        JsonNode resp = postJson(MSG_CHECK_URL, token, body);
        int errcode = resp == null ? -1 : resp.path("errcode").asInt(0);
        String errmsg = resp == null ? "no-response（网络不通 / 超时）" : resp.path("errmsg").asText("");
        String suggest = resp == null ? "" : resp.path("result").path("suggest").asText("");
        int label = resp == null ? 0 : resp.path("result").path("label").asInt(0);
        boolean ok = resp != null && (errcode == 0 || errcode == ERR_RISKY);

        probeInfo.put("ok", ok);
        probeInfo.put("openid", mask(openid));
        probeInfo.put("text", text);
        probeInfo.put("errcode", errcode);
        probeInfo.put("errmsg", errmsg);
        probeInfo.put("suggest", suggest);
        probeInfo.put("label", label);
        probeInfo.put("verdict", verdictOf(errcode, suggest));

        if (errcode != 0 && errcode != ERR_RISKY) {
            hints.add("msg_sec_check 调用失败 errcode=" + errcode + " errmsg=" + errmsg
                    + (errcode == 61010 ? "（该用户 2 年内未访问过小程序，openid 已失效）" : ""));
            return;
        }
        // 通了但没结果字段，通常是打错了接口（v1 不返回 result.suggest）
        if (errcode == 0 && !StringUtils.hasText(suggest)) {
            hints.add("接口通了但没有 result.suggest：确认请求的是 /wxa/msg_sec_check 且 version=2");
        }
    }

    private static String verdictOf(int errcode, String suggest) {
        if (errcode == ERR_RISKY) {
            return "risky";
        }
        if (errcode != 0) {
            return "error";
        }
        return StringUtils.hasText(suggest) ? suggest : "unknown";
    }

    /** 凭据脱敏：保留首尾各 4 位，既能核对又不至于把密钥写进日志/响应体 */
    private static String mask(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        if (raw.length() <= 10) {
            return raw.charAt(0) + "****";
        }
        return raw.substring(0, 4) + "****" + raw.substring(raw.length() - 4);
    }
}
