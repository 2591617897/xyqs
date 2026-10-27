package com.campus.user.dto;

import lombok.Data;

/**
 * 自动入群请求（campus-user → campus-message）。
 *
 * <p>只带 userId + schoolId：群名由 message 服务自己查 {@code t_school.name}
 * （各服务本地共库），省一次跨服务调用。
 */
@Data
public class AutoJoinReq {

    private Long userId;

    private Long schoolId;

    public AutoJoinReq() {
    }

    public AutoJoinReq(Long userId, Long schoolId) {
        this.userId = userId;
        this.schoolId = schoolId;
    }
}
