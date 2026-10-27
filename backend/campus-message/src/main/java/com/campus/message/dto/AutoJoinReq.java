package com.campus.message.dto;

import lombok.Data;

/**
 * 自动入群请求（campus-user → campus-message，走 /internal/groups/auto-join）。
 *
 * <p>只需要 userId + schoolId：群名由 message 侧直接取 {@code t_school.name}
 * （各服务本地共库），省一次跨服务调用，也避免 user 侧多查一次学校。
 */
@Data
public class AutoJoinReq {

    private Long userId;

    private Long schoolId;
}
