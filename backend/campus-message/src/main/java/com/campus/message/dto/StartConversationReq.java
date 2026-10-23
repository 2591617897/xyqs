package com.campus.message.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起（或获取）一个私信会话。
 * 商品快照字段由调用方带上，避免消息服务跨域查商品库。
 */
@Data
public class StartConversationReq {

    /** 对端用户 id */
    @NotNull(message = "缺少聊天对象")
    private Long peerId;

    /** 关联商品 id，非商品会话不传 */
    private Long goodsId;

    private String goodsTitle;

    private String goodsPrice;

    private String goodsEmoji;

    /** 0已下架 1在售 2已售出 */
    private Integer goodsStatus;

    /** 1 匿名会话（表白墙回声） */
    private Integer anon;

    /** 匿名会话展示名，如「匿名纸条 #302号」 */
    private String anonName;
}
