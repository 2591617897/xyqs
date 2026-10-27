package com.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息提醒设置：站内开关 + 免打扰时段 + 微信订阅消息授权状态。
 *
 * <p>一个用户一行（userId 即主键）。没记录表示用户从未改过设置，
 * 读取时由 Service 返回默认值（全开、免打扰关、未授权），不预插空行。
 */
@Data
@TableName("t_message_setting")
public class MessageSetting {

    /** 用户 id，主键 */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /** 1 开 / 0 关 —— 私信 */
    private Integer notifyMessage;
    /** 赞与收藏 */
    private Integer notifyLike;
    /** 新增粉丝 */
    private Integer notifyFollow;
    /** 评论与@ */
    private Integer notifyComment;
    /** 官方通知 */
    private Integer notifySystem;

    /** 免打扰开关 */
    private Integer dndEnabled;
    /** 免打扰开始 HH:mm */
    private String dndStart;
    /** 免打扰结束 HH:mm，允许跨天（22:00 → 07:00） */
    private String dndEnd;

    /** 微信订阅消息：0 未授权 / 1 已授权 / 2 已拒绝 */
    private Integer subscribeStatus;

    private LocalDateTime subscribeAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
