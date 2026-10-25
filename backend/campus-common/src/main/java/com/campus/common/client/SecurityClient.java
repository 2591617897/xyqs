package com.campus.common.client;

import com.campus.common.api.R;
import com.campus.common.client.dto.MediaCheckReq;
import com.campus.common.client.dto.MsgCheckReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 内容安全内部接口客户端（微信 msg_sec_check / media_check_async 能力在 campus-auth）。
 *
 * 业务侧请统一通过 {@link SecurityGuard} 调用，不要直接用它 —— 后者负责
 * fail-open 与异常语义，直接调会把微信侧故障放大成全站发不出内容。
 */
@FeignClient(name = "campus-auth", contextId = "campusSecurityClient", path = "/internal/security")
public interface SecurityClient {

    /** 文本同步校验；命中违规返回 code=400 */
    @PostMapping("/msg-check")
    R<Void> msgCheck(@RequestBody MsgCheckReq req);

    /** 图片/音频异步送审；返回实际提交成功的条数 */
    @PostMapping("/media-check")
    R<Integer> mediaCheck(@RequestBody MediaCheckReq req);
}
