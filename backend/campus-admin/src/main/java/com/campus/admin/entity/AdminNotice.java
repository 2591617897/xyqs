package com.campus.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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

    private LocalDateTime publishAt;

    private LocalDateTime offlineAt;

    private Long publisherId;

    private String publisherName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
