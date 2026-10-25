package com.campus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容处置流水（t_content_audit）—— 本服务只写「系统自动拦截」这一类。
 *
 * <p>这张表原本由 campus-admin 在人工处置时写（action 为 HIDE/DELETE/PASS/WARN，
 * source=1）。内容安全拦截属于同一条链路上的另一种来源，所以复用同一张表：
 * <ul>
 *   <li>source = 2（系统自动）；</li>
 *   <li>action = REJECT（拒绝发布，内容根本没落库）/ WARN（疑似，已放行待复核）；</li>
 *   <li>target_id = 0 表示「拦截时还没有内容 id」，后台按 target_type + 时间就能查到。</li>
 * </ul>
 * 复用而不是新开表，是为了让后台已有的「内容审核记录」页面（ContentController.pageAudits）
 * 天然能看到这些拦截记录，不必再做一个入口。
 */
@Data
@TableName("t_content_audit")
public class ContentAuditTrail {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** POST / WALL / GOODS / *_COMMENT / MESSAGE / NICKNAME / UNKNOWN */
    private String targetType;

    /** 业务主键；拦截时内容还没落库，填 0 */
    private Long targetId;

    private String title;

    /** 违规内容摘要（截断 200 字） */
    private String contentBrief;

    private String images;

    private Long authorId;

    private String authorName;

    private Long schoolId;

    /** REJECT / WARN */
    private String action;

    private String reasonType;

    private String reason;

    private Long adminId;

    private String adminName;

    /** 1 后台人工 / 2 系统自动 / 3 举报处理 */
    private Integer source;

    private LocalDateTime createdAt;
}
