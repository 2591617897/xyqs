package com.campus.message.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 消息中心首页：顶部统计 + 会话列表 */
@Data
public class MessageCenterVO {

    /** unreadTotal / likes / mentions / followers / notice */
    private Map<String, Object> summary;

    private List<ConversationVO> convos;
}
