package com.campus.user.client;

import com.campus.common.api.R;
import com.campus.common.client.dto.StudentCheckReq;
import com.campus.common.client.dto.StudentCheckVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 微信学生身份核验客户端（campus-user → campus-auth）。
 *
 * <p>放在本模块而不是 campus-common：campus-auth 自己也 {@code @EnableFeignClients}
 * 扫了 common.client，把客户端放 common 会让 auth 注册一个指向自己的代理，
 * 虽然无害但容易在排查时误导。
 *
 * <p>失败语义由 auth 统一给出（401 之外一律 HTTP 200 + code!=0），
 * 调用方只需把 msg 透传，不要自己再编一套话术。
 */
@FeignClient(name = "campus-auth", contextId = "studentVerifyClient", path = "/internal/wx/student")
public interface StudentVerifyClient {

    @PostMapping("/check")
    R<StudentCheckVO> check(@RequestBody StudentCheckReq req);
}
