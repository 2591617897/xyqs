package com.campus.message.dto;

import lombok.Data;

/**
 * 禁言记录（后台列表用）。{@code endAt} 为空表示永久。
 */
@Data
public class GroupMuteVO {

    private String id;

    private String groupId;

    private String userId;

    private String nickname;

    private String avatar;

    private String startAt;

    /** 空 = 永久 */
    private String endAt;

    private String reason;

    private String operatorId;

    /** 1 生效 0 已解除 */
    private Integer status;
}
