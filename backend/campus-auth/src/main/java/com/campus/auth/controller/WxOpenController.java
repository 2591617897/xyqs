package com.campus.auth.controller;

import com.campus.auth.service.WxSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信「消息推送」回调入口（公网）：接收 media_check_async 的图片审核结果。
 *
 * <p>为什么单独一个 Controller：/internal/** 只在 Nacos 集群内可达，
 * 微信服务器从公网访问不到，所以这条必须走网关（/api/wx/** → 本服务 /wx/**），
 * 并单独在 AuthGlobalFilter 白名单里放行（它自带签名校验，不需要用户令牌）。
 *
 * <p>配置步骤（微信公众平台 → 开发管理 → 开发设置 → 消息推送）：
 * <pre>
 *   URL   : https://&lt;你的域名&gt;/api/wx/callback
 *   Token : 与 campus.wx.callback-token 一致
 *   数据格式: JSON（必须是 JSON，XML 无法解析媒体审核事件）
 *   加密方式: 明文模式（安全模式需额外实现 AES 解密，本项目未启用）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/wx")
@RequiredArgsConstructor
public class WxOpenController {

    private final WxSecurityService wxSecurityService;

    /**
     * 微信验证服务器地址时用：验签通过原样返回 echostr。
     */
    @GetMapping("/callback")
    public String verify(@RequestParam(name = "signature", required = false) String signature,
                         @RequestParam(name = "timestamp", required = false) String timestamp,
                         @RequestParam(name = "nonce", required = false) String nonce,
                         @RequestParam(name = "echostr", required = false) String echostr) {
        if (!wxSecurityService.verifySignature(signature, timestamp, nonce)) {
            log.warn("[wx-callback] 验签失败，拒绝（signature={}）", signature);
            return "";
        }
        return echostr == null ? "" : echostr;
    }

    /**
     * 接收事件推送。必须返回 success，否则微信会重试（最多 3 次）。
     */
    @PostMapping("/callback")
    public String receive(@RequestParam(name = "signature", required = false) String signature,
                          @RequestParam(name = "timestamp", required = false) String timestamp,
                          @RequestParam(name = "nonce", required = false) String nonce,
                          @RequestBody(required = false) String body) {
        if (!wxSecurityService.verifySignature(signature, timestamp, nonce)) {
            log.warn("[wx-callback] 验签失败，忽略本次推送");
            return "invalid signature";
        }
        try {
            wxSecurityService.handleCallback(body);
        } catch (Exception e) {
            // 回调处理异常也不能让微信一直重试（重试也是同样的异常）
            log.error("[wx-callback] 处理回调异常：{}", e.getMessage(), e);
        }
        return "success";
    }
}
