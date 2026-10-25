package com.campus.auth.controller;

import com.campus.auth.dto.MsgCheckReq;
import com.campus.auth.service.WxSecurityService;
import com.campus.common.api.R;
import com.campus.common.client.dto.MediaCheckReq;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内容安全内部接口。
 *
 * /internal/** 不在网关路由里，只能由同一 Nacos 集群内的服务通过 Feign 直连，
 * 与既有的 /internal/posts/count、/internal/users 一致，无需额外鉴权。
 *
 * <p><b>不要往这里加公网入口</b>：微信回调走 {@link WxOpenController}。
 */
@RestController
@RequestMapping("/internal/security")
@RequiredArgsConstructor
public class InternalSecurityController {

    private final WxSecurityService wxSecurityService;

    /** 文本内容安全校验；命中违规返回 code=400，其余情况一律 code=0 */
    @PostMapping("/msg-check")
    public R<Void> msgCheck(@RequestBody MsgCheckReq req) {
        if (req != null) {
            wxSecurityService.checkText(req.userId(), req.content(), req.scene(), req.bizType());
        }
        return R.ok();
    }

    /**
     * 图片异步送审。返回实际提交成功的条数（0 表示没送出去：开关关、无 openid、
     * 非公网图片或微信侧异常，此时业务照常发布，属 fail-open 设计）。
     */
    @PostMapping("/media-check")
    public R<Integer> mediaCheck(@RequestBody MediaCheckReq req) {
        return R.ok(wxSecurityService.submitMediaCheck(req));
    }

    /** 探活：审核开关是否真正生效（开关开 + 凭据齐备） */
    @GetMapping("/enabled")
    public R<Map<String, Object>> enabled() {
        return R.ok(Map.of(
                "text", wxSecurityService.active(),
                "media", wxSecurityService.mediaActive(),
                "callback", wxSecurityService.callbackReady()));
    }
}
