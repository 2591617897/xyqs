package com.campus.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 恢复注销账号的返回体。
 *
 * <p>必须带新 token：恢复时学校、认证状态可能变化，登录时签发的那张令牌里
 * {@code schoolId / vs} 是注销瞬间的快照，不重签前端会继续按旧状态渲染。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestoreResp {

    private String token;

    private UserBrief user;
}
