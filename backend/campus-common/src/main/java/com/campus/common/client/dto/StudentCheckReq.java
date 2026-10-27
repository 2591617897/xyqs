package com.campus.common.client.dto;

/**
 * 微信学生身份核验入参（campus-user → campus-auth）。
 *
 * <p>传 userId 而不是 openid：openid 只在 campus-auth 的库里有直读能力
 * （{@code WxOpenidMapper}），业务侧不该为了一个 openid 去反查用户表。
 *
 * <p><b>code 是敏感凭据</b>：两小时有效、一次性，服务端只做转发，不落库、不打日志。
 *
 * @param userId 当前登录用户 id（auth 侧据此查 openid）
 * @param code   授权插件 bind:auth 回调里的 wx_studentcheck_code
 */
public record StudentCheckReq(Long userId, String code) {
}
