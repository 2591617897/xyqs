package com.campus.auth.controller;

import com.campus.auth.service.WxStudentService;
import com.campus.common.api.R;
import com.campus.common.client.dto.StudentCheckReq;
import com.campus.common.client.dto.StudentCheckVO;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信学生身份核验内部接口（campus-user → campus-auth）。
 *
 * <p>/internal/** 不在网关路由里，只能由同一 Nacos 集群内的服务通过 Feign 直连，
 * 与 /internal/security、/internal/posts 一致，不需要额外鉴权。
 *
 * <p><b>不要把学生核验做成公网接口</b>：它需要 appsecret 换 token，
 * 且入参里带一次性 code，暴露出去等于给刷接口的人开了一扇门。
 */
@RestController
@RequestMapping("/internal/wx/student")
@RequiredArgsConstructor
public class InternalWxStudentController {

    private final WxStudentService wxStudentService;

    @PostMapping("/check")
    public R<StudentCheckVO> check(@RequestBody StudentCheckReq req) {
        if (req == null || req.userId() == null) {
            throw new BizException("缺少用户标识，无法核验学生身份");
        }
        return R.ok(wxStudentService.check(req.userId(), req.code()));
    }
}
