package com.campus.message.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.message.dto.MessageSettingsVO;
import com.campus.message.service.MessageSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息提醒设置（前端走网关 /api/messages/settings）。
 *
 * <p>这三个接口都是私域数据，必须登录：网关的 PUBLIC_READ_PREFIX 不包含 /api/messages，
 * 所以不会出现「游客拿到别人的设置」这种问题。
 */
@RestController
@RequestMapping("/messages/settings")
@RequiredArgsConstructor
public class MessageSettingController {

    private final MessageSettingService settingService;

    /**
     * 读取当前用户的提醒设置。
     *
     * <p>响应里额外带 {@code templateIds} / {@code wxPushAvailable}：
     * 订阅消息模板还没配时前端应「只展示入口、不发起授权」，
     * 拿空数组去调 wx.requestSubscribeMessage 会直接失败。
     */
    @GetMapping
    public R<MessageSettingsVO> get() {
        return R.ok(settingService.get(UserContext.requireUserId()));
    }

    /** 保存开关与免打扰（订阅状态只能由微信授权结果改，这里不接收） */
    @PutMapping
    public R<MessageSettingsVO> save(@RequestBody MessageSettingsVO req) {
        return R.ok(settingService.save(UserContext.requireUserId(), req));
    }

    /**
     * 上报订阅消息授权结果。
     *
     * @param status 1 已允许 / 2 已拒绝 / 0 重置
     */
    @PostMapping("/subscribe")
    public R<MessageSettingsVO> subscribe(@RequestParam(defaultValue = "0") int status) {
        return R.ok(settingService.markSubscribe(UserContext.requireUserId(), status));
    }
}
