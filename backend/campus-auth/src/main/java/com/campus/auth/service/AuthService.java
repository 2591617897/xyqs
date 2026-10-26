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
import com.campus.auth.security.LoginRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 解析第三方接口的纯文本响应。
     * 微信/QQ 的 code2Session 响应头是 text/plain，不能交给 RestTemplate 的
     * Jackson 转换器（它只认 application/json），只能取字符串后手动解析。
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** IP 定位缓存：TTL 6 小时、最多 512 个 IP —— 腾讯 key 每日配额有限，能复用就复用 */
    private static final long IP_GEO_TTL_MS = 6 * 60 * 60 * 1000L;
    private static final int IP_GEO_CACHE_MAX = 512;
    private final Map<String, CachedGeo> ipGeoCache = new ConcurrentHashMap<>();

    private final SchoolMapper schoolMapper;
    private final UserClient userClient;
    private final JwtService jwtService;
    private final RestTemplate restTemplate;
    private final LoginRateLimiter loginRateLimiter;

    @Value("${campus.qq.appid:}")
    private String qqAppid;

    @Value("${campus.qq.appsecret:}")
    private String qqAppsecret;

    @Value("${campus.wx.appid:}")
    private String wxAppid;

    @Value("${campus.wx.appsecret:}")
    private String wxAppsecret;

    /** 默认关闭 mock：mock 不校验真实身份，会让多个微信账号合并成同一个用户 */
    @Value("${campus.auth.mock:false}")
    private boolean mock;

    /** 腾讯位置服务 key（逆地理编码用），未配置时位置解析优雅降级 */
    @Value("${campus.geo.qqmap-key:}")
    private String qqmapKey;

    /**
     * 腾讯位置服务「签名校验」的 SK。
     * key 在控制台开启了签名校验时必填，否则 WebService 会返回 status=111 签名验证失败；
     * 未开启签名校验时留空即可（请求不带 sig）。
     */
    @Value("${campus.geo.qqmap-sk:}")
    private String qqmapSk;

    public LoginResp login(LoginReq req) {
        if (!StringUtils.hasText(req.getCode()) && !StringUtils.hasText(req.getDevOpenid())) {
            throw new BizException("缺少登录凭证 code");
        }
        // 先只按 IP 挡一层：此时还没拿到 openid，但已经能挡住刷接口的脚本，
        // 顺便保护 code2Session 的日调用配额（被刷等于小程序登录整体不可用）。
        String ip = clientIp();
        loginRateLimiter.check(ip, null);

        String platform = normalizePlatform(req.getPlatform());
        Session session = code2Session(platform, req.getCode(), req.getDevOpenid());
        // 拿到 openid 后再按账号维度挡一层，防止「同一账号换 IP 高频换令牌」
        loginRateLimiter.check(ip, session.openid());
        // 便于核对：两个微信号登录应当打印出两个不同的 openid
        log.info("登录 platform={} openid={}", platform, maskOpenid(session.openid()));

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
     *
     * key 若在控制台开启了「签名校验」，请求必须带 sig：
     *   sig = MD5(请求路径?参数按名升序拼接 + SK)
     * 否则返回 status=111（签名验证失败）。未配置 SK 时按不带签名请求发送。
     * 任何失败都降级为 available=false，由前端回退到「学校所在市」。
     */
    public GeoCity resolveCity(double lat, double lng) {
        GeoCity resp = new GeoCity();
        resp.setAvailable(false);
        // TreeMap 天然按参数名升序，与腾讯签名要求的排序一致
        Map<String, String> params = new TreeMap<>();
        params.put("location", String.format(Locale.ROOT, "%s,%s", lat, lng));
        params.put("get_poi", "0");

        Map<?, ?> body = qqmapGet("/ws/geocoder/v1/", params);
        Object result = body == null ? null : body.get("result");
        Object comp = result instanceof Map ? ((Map<?, ?>) result).get("address_component") : null;
        if (comp instanceof Map) {
            Map<?, ?> c = (Map<?, ?>) comp;
            resp.setProvince(asText(c.get("province")));
            resp.setCity(asText(c.get("city")));
            resp.setDistrict(asText(c.get("district")));
            resp.setAvailable(StringUtils.hasText(resp.getCity()));
        }
        return resp;
    }

    /**
     * IP 定位：客户端 IP → 省/市/区（腾讯位置服务 IP 定位）。
     *
     * 相比 wx.getLocation：不需要用户授权、不弹隐私授权框，代价是精度只到城市级 ——
     * 正好够「推荐同城学校」。内网/回环地址不传 ip 参数，让腾讯按请求来源解析
     * （本地开发时就是开发机出口 IP，方便在开发者工具里直接看效果）。
     * 任何失败都降级 available=false，前端静默不显示推荐区。
     */
    public GeoCity resolveCityByIp(String ip) {
        GeoCity resp = new GeoCity();
        resp.setAvailable(false);
        String cacheKey = StringUtils.hasText(ip) ? ip.trim() : "local";
        CachedGeo hit = ipGeoCache.get(cacheKey);
        if (hit != null && System.currentTimeMillis() - hit.at() < IP_GEO_TTL_MS) {
            return hit.geo();
        }
        Map<String, String> params = new TreeMap<>();
        if (StringUtils.hasText(ip) && !isPrivateOrLoopbackIp(ip)) {
            params.put("ip", ip.trim());
        }
        Map<?, ?> body = qqmapGet("/ws/location/v1/ip", params);
        Object result = body == null ? null : body.get("result");
        Object ad = result instanceof Map ? ((Map<?, ?>) result).get("ad_info") : null;
        if (!(ad instanceof Map)) {
            // 失败不缓存：下次打开还能重试（腾讯 key 有每日配额，配额耗尽时别反复打）
            return resp;
        }
        Map<?, ?> info = (Map<?, ?>) ad;
        resp.setProvince(asText(info.get("province")));
        resp.setCity(asText(info.get("city")));
        resp.setDistrict(asText(info.get("district")));
        resp.setAvailable(StringUtils.hasText(resp.getCity()));
        if (resp.isAvailable() && ipGeoCache.size() < IP_GEO_CACHE_MAX) {
            ipGeoCache.put(cacheKey, new CachedGeo(resp, System.currentTimeMillis()));
        }
        return resp;
    }

    /** IP 定位结果缓存：一整个校园网通常共用一个出口 IP，没必要每个用户都查一次（省腾讯配额） */
    private record CachedGeo(GeoCity geo, long at) {}

    /**
     * 调腾讯位置服务 WebService：自动补 key/output、按参数名排序、按需加 sig。
     *
     * 未配置 key、网络异常、status != 0 统一返回 null 由调用方降级 ——
     * 位置能力只做锦上添花，永远不许打崩业务。
     */
    private Map<?, ?> qqmapGet(String path, Map<String, String> params) {
        if (!StringUtils.hasText(qqmapKey)) {
            log.info("campus.geo.qqmap-key 未配置，位置能力降级为不可用");
            return null;
        }
        try {
            Map<String, String> sorted = new TreeMap<>(params);
            sorted.put("key", qqmapKey);
            sorted.put("output", "json");

            StringBuilder qs = new StringBuilder();
            for (Map.Entry<String, String> e : sorted.entrySet()) {
                if (qs.length() > 0) {
                    qs.append('&');
                }
                qs.append(e.getKey()).append('=').append(encodeValue(e.getValue()));
            }
            String query = qs.toString();
            if (StringUtils.hasText(qqmapSk)) {
                // 参与签名的只有 path + 排序后的参数（不含 sig 自身）
                query = query + "&sig=" + md5Hex(path + "?" + query + qqmapSk);
            }

            String url = "https://apis.map.qq.com" + path + "?" + query;
            // 先取字符串再解析：腾讯位置服务偶尔返回 text/plain，
            // 直接要 Map 会抛转换异常并被静默降级
            String raw = restTemplate.getForObject(url, String.class);
            Map<?, ?> body = StringUtils.hasText(raw) ? JSON.readValue(raw, Map.class) : null;
            Object status = body == null ? null : body.get("status");
            if (body != null && status != null && !"0".equals(String.valueOf(status))) {
                log.warn("腾讯位置服务返回异常 path={} status={} message={}", path, status, body.get("message"));
                if ("111".equals(String.valueOf(status)) && !StringUtils.hasText(qqmapSk)) {
                    log.warn("腾讯位置服务要求签名：请在控制台关闭该 key 的「签名校验」，"
                            + "或把 SK 配到 campus.geo.qqmap-sk（环境变量 QQMAP_SK）");
                }
                return null;
            }
            return body;
        } catch (Exception e) {
            log.warn("腾讯位置服务请求失败 path={} {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 内网 / 回环 / 链路本地 / CGNAT 地址：这类 IP 拿去做定位只会得到机房城市，没有意义。
     * 统一按「不可直接查询」处理，改为让腾讯按请求来源解析。
     */
    private boolean isPrivateOrLoopbackIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return true;
        }
        String v = ip.trim();
        if (v.startsWith("::ffff:")) {
            v = v.substring(7); // IPv4-mapped IPv6
        }
        if (v.indexOf(':') >= 0) {
            return "::1".equals(v); // 其它 IPv6 不做处理
        }
        String[] parts = v.split("\\.");
        if (parts.length != 4) {
            return true;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a == 127 || a == 10) return true;
            if (a == 192 && b == 168) return true;
            if (a == 169 && b == 254) return true;
            if (a == 172 && b >= 16 && b <= 31) return true;
            if (a == 100 && b >= 64 && b <= 127) return true; // CGNAT
            if (a == 0) return true;
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * 腾讯 WebService 签名用的最小化 URL 编码。
     *
     * 只转义会破坏 query 结构的字符，`,` `:` `-` `.` `_` `~` 一律保留原样 ——
     * 腾讯官方示例里 `location=39.98,116.30` 的逗号是不编码的，
     * 若直接用 URLEncoder 会把逗号变成 %2C，导致签名与请求串不一致、校验失败。
     */
    private String encodeValue(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("%", "%25")
                .replace(" ", "%20")
                .replace("+", "%2B")
                .replace("&", "%26")
                .replace("=", "%3D")
                .replace("#", "%23")
                .replace("?", "%3F");
    }

    /** MD5 小写 32 位（腾讯 WebService 签名要求） */
    private String md5Hex(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(src.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String h = Integer.toHexString(b & 0xFF);
                if (h.length() == 1) {
                    sb.append('0');
                }
                sb.append(h);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("签名计算失败");
        }
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
     *
     * mock 只用于本地单人演示：它不做真实的 code2Session，openid 来自请求里的
     * devOpenid 或按 code 哈希，因此**多个微信账号会合并到同一个用户**。
     * 之前这里在「appid 未配置」时也静默降级成 mock，正是多账号同号的根因，
     * 现在改为明确报配置缺失，不再静默降级。
     */
    private Session code2Session(String platform, String code, String devOpenid) {
        boolean qq = "qq".equals(platform);
        String label = qq ? "QQ" : "微信";
        String appid = qq ? qqAppid : wxAppid;
        String appsecret = qq ? qqAppsecret : wxAppsecret;

        if (mock) {
            String openid = StringUtils.hasText(devOpenid)
                    ? devOpenid
                    : "mock_" + platform + "_" + Math.abs(String.valueOf(code).hashCode());
            log.warn("[mock] code2Session platform={} openid={} —— mock 模式下不同宿主账号会落到同一用户，仅限单人演示",
                    platform, maskOpenid(openid));
            return new Session(openid, null);
        }

        if (!StringUtils.hasText(appid) || !StringUtils.hasText(appsecret)) {
            log.error("{} 登录缺少 appid/appsecret 配置（appid 是否为空={}，appsecret 是否为空={}）",
                    label, !StringUtils.hasText(appid), !StringUtils.hasText(appsecret));
            throw new BizException(label + "登录未配置：请设置环境变量 "
                    + (qq ? "QQ_APPID / QQ_APPSECRET" : "WX_APPID / WX_APPSECRET"));
        }

        String url = qq
                ? String.format(
                "https://api.q.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appid, appsecret, code)
                : String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appid, appsecret, code);

        // 注意：微信/QQ 的这个接口响应头是 text/plain（body 才是 JSON），
        // 直接 getForObject(url, Map.class) 会抛
        // UnknownContentTypeException: no suitable HttpMessageConverter ... text/plain。
        // 所以统一先取字符串，再自己解析，顺便把原始 body 打进日志便于排查。
        String raw;
        try {
            raw = restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("code2Session 请求异常 platform={} {}", platform, e.getMessage());
            throw new BizException(label + "登录失败：无法连接" + label + "服务器，请稍后重试");
        }
        if (!StringUtils.hasText(raw)) {
            log.error("code2Session 空响应 platform={}", platform);
            throw new BizException(label + "登录失败：返回空响应");
        }

        Map<?, ?> body;
        try {
            body = JSON.readValue(raw, Map.class);
        } catch (Exception e) {
            log.error("code2Session 响应非 JSON platform={} raw={}", platform, raw);
            throw new BizException(label + "登录失败：返回格式异常");
        }

        Object openid = body.get("openid");
        if (openid == null) {
            Object errcode = body.get("errcode");
            Object errmsg = body.get("errmsg");
            log.warn("code2Session 失败 platform={} errcode={} errmsg={}", platform, errcode, errmsg);
            throw new BizException(label + "登录失败：" + describeSessionError(errcode, errmsg));
        }
        log.info("code2Session 成功 platform={} openid={}", platform, maskOpenid(String.valueOf(openid)));
        return new Session(String.valueOf(openid),
                body.get("unionid") == null ? null : String.valueOf(body.get("unionid")));
    }

    /** 把微信的 errcode 翻成人话，前端 toast 才有意义 */
    private String describeSessionError(Object errcode, Object errmsg) {
        String code = errcode == null ? "" : String.valueOf(errcode);
        switch (code) {
            case "40013":
                return "AppID 无效，请核对小程序 appid";
            case "40125":
            case "41004":
                return "AppSecret 无效，请在微信公众平台重置后更新配置";
            case "40029":
            case "40163":
                return "登录凭证 code 已失效，请重新进入小程序";
            case "45011":
                return "登录过于频繁，请稍后再试";
            case "40226":
                return "该账号被限制登录，请联系管理员";
            default:
                String msg = errmsg == null ? "未知错误" : String.valueOf(errmsg);
                return code.isEmpty() ? msg : msg + "（errcode=" + code + "）";
        }
    }

    /** openid 打码后再进日志：既能核对「不同微信是否落到不同 openid」，又不会把凭证原样落盘 */
    private String maskOpenid(String openid) {
        if (!StringUtils.hasText(openid) || openid.length() <= 10) {
            return openid;
        }
        return openid.substring(0, 6) + "****" + openid.substring(openid.length() - 4);
    }

    /**
     * 客户端真实 IP。生产在 Nginx 后面，{@code remoteAddr} 永远是 127.0.0.1，
     * 必须优先取 {@code X-Forwarded-For} 的第一段（Nginx 用 {@code $proxy_add_x_forwarded_for} 追加）。
     */
    private String clientIp() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest request = attrs.getRequest();
                String xff = request.getHeader("X-Forwarded-For");
                if (StringUtils.hasText(xff)) {
                    return xff.split(",")[0].trim();
                }
                String real = request.getHeader("X-Real-IP");
                if (StringUtils.hasText(real)) {
                    return real.trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            // 拿不到 IP 不影响登录，只是少一层限流维度
            log.debug("获取客户端 IP 失败：{}", e.getMessage());
        }
        return null;
    }

    private record Session(String openid, String unionid) {
    }
}
