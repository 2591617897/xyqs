package com.campus.common.client;

import com.campus.common.api.R;
import com.campus.common.client.dto.MsgCheckReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 内容安全内部接口客户端（微信 msg_sec_check 能力在 campus-auth）。
 *
 * 业务侧请统一通过 {@link SecurityGuard} 调用，不要直接用它 —— 后者负责
 * fail-open 与异常语义，直接调会把微信侧故障放大成全站发帖失败。
 */
@FeignClient(name = "campus-auth", contextId = "campusSecurityClient", path = "/internal/security")
public interface SecurityClient {

    @PostMapping("/msg-check")
    R<Void> msgCheck(@RequestBody MsgCheckReq req);
}
