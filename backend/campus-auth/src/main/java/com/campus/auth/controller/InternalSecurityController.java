package com.campus.auth.controller;

import com.campus.auth.dto.MsgCheckReq;
import com.campus.auth.service.LocalSensitiveGuard;
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
    private final LocalSensitiveGuard localSensitiveGuard;

    /**
     * 文本内容安全校验；命中违规返回 code=400，其余情况一律 code=0。
     *
     * <p>先把 {@link LocalSensitiveGuard} 的禁止类（level 3）词在这里兜住：
     * 微信审核依赖 appid/appsecret，凭据缺失或还没配好时 {@code active()} 为 false 会直接
     * 跳过微信那一段，如果不在本地先拦，运营的词库在那种情况下就完全失效了。
     */
    @PostMapping("/msg-check")
    public R<Void> msgCheck(@RequestBody MsgCheckReq req) {
        if (req != null) {
            localSensitiveGuard.purify(req.userId(), req.content(), req.bizType());
            wxSecurityService.checkText(req.userId(), req.content(), req.scene(), req.bizType());
        }
        return R.ok();
    }

    /**
     * 本地敏感词净化：level 3 拦、level 2 替换、level 1 留痕，返回处理后的文本。
     *
     * <p>与 /msg-check 的区别：它<b>不调微信</b>，纯内存词表匹配，
     * 所以每个字段都能放心单独调用（成本远低于一次 HTTP）。业务侧拿返回值落库，
     * level 2 的替换才真正生效。
     */
    @PostMapping("/purify")
    public R<String> purify(@RequestBody MsgCheckReq req) {
        if (req == null || req.content() == null) {
            return R.ok(null);
        }
        return R.ok(localSensitiveGuard.purify(req.userId(), req.content(), req.bizType()));
    }

    /**
     * 图片异步送审。返回实际提交成功的条数（0 表示没送出去：开关关、无 openid、
     * 非公网图片或微信侧异常，此时业务照常发布，属 fail-open 设计）。
     */
    @PostMapping("/media-check")
    public R<Integer> mediaCheck(@RequestBody MediaCheckReq req) {
        return R.ok(wxSecurityService.submitMediaCheck(req));
    }

    /** 探活：审核开关是否真正生效（开关开 + 凭据齐备）；附带本地词库规模 */
    @GetMapping("/enabled")
    public R<Map<String, Object>> enabled() {
        return R.ok(Map.of(
                "text", wxSecurityService.active(),
                "media", wxSecurityService.mediaActive(),
                "callback", wxSecurityService.callbackReady(),
                // 本地词不依赖任何凭据，size>0 就意味着它在守着
                "localWords", localSensitiveGuard.size()));
    }

    /** 运营在后台改完词立刻调用；失败也不影响（服务端另有定时刷新兜底） */
    @PostMapping("/words/reload")
    public R<Integer> reloadWords() {
        localSensitiveGuard.reload();
        return R.ok(localSensitiveGuard.size());
    }
}
