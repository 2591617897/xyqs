package com.campus.message.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话列表项 / 会话详情。
 * 列表接口不填充 messages，详情接口填充。
 */
@Data
public class ConversationVO {

    private Long id;

    private Long peerId;

    private String peerName;

    private String peerAvatar;

    private Boolean peerVerified;

    /** 预留：暂无在线状态服务，固定 false */
    private Boolean peerOnline;

    private List<PeerTag> peerTags;

    /** 匿名会话（表白墙回声） */
    private Boolean anon;

    private Integer unread;

    private GoodsCard goodsCard;

    private String lastText;

    /**
     * 最后一条消息的发送方：me 我发的 / peer 对方发的。
     * 前端据此显示「我：」前缀。列表接口填充，详情接口也填充。
     */
    private String lastFrom;

    private LocalDateTime lastTime;

    /** 会话详情：聊天记录，按时间升序 */
    private List<MessageVO> messages;
}
