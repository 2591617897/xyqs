package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知公告。草稿 → 已发布 → 已下线，publishAt 支持预约发布。
 */
@Data
@TableName("t_admin_notice")
public class AdminNotice {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    private String content;

    /** 1系统公告 2运营活动 3违规警示 */
    private Integer type;

    /** 1后台管理员 2小程序全体 3指定校区 */
    private Integer targetType;

    private Long schoolId;

    /** 1普通 2重要 3紧急 */
    private Integer level;

    /**
     * 小程序弹窗频次：0不弹窗 1每次进入 2每天一次 3确认收到后不再显示。
     *
     * <p>0 等同于旧行为（只出现在首页公告条）；大于 0 时用户进入小程序会被弹窗打断，
     * 频次完全由运营选择，服务端只负责按 {@code t_notice_receipt} 判定是否该弹。
     */
    private Integer popupMode;

    /** 0草稿 1已发布 2已下线 */
    private Integer status;

    /**
     * 发布时间。可以填未来时间做<b>预约发布</b>：状态先置为已发布，
     * C 端按 {@code publish_at <= NOW()} 过滤，到点自然出现，不需要定时任务推。
     *
     * <p>显式声明格式是为了和运营后台的 el-date-picker 对齐：
     * 全局 Jackson 没配 LocalDateTime 格式，默认是 ISO 的 {@code 2026-10-27T02:00:00}，
     * 运营在表单里看到带 T 的时间既别扭、手改也容易出错。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishAt;

    /**
     * 下线时间。运营填未来时间即为<b>定时下线</b>：C 端立刻按它过滤，
     * 后台状态由 {@code NoticeExpireJob} 每 5 分钟翻成「已下线」。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime offlineAt;

    private Long publisherId;

    private String publisherName;

    /**
     * 弹窗送达人数（去重登录用户数）。来自 t_notice_receipt 的聚合，
     * 不是本表字段，所以标 exist=false —— 否则 insert/update 会带上这两列直接报错。
     */
    @TableField(exist = false)
    private Long shownCount;

    /** 其中点了「我知道了」的人数 */
    @TableField(exist = false)
    private Long confirmCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
