package com.campus.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.config.AdminContext;
import com.campus.admin.config.DataScope;
import com.campus.admin.config.DataScopeResolver;
import com.campus.admin.dto.ReportHandleReq;
import com.campus.admin.entity.ReportTicket;
import com.campus.admin.mapper.ReportTicketMapper;
import com.campus.common.api.R;
import com.campus.common.client.NotificationClient;
import com.campus.common.client.dto.NotifyReq;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 举报工单处理。
 *
 * <p>工单由小程序侧（campus-user 的 ReportService）创建，后台只负责受理，
 * 两侧共享 t_report_ticket，靠 status 流转区分：0待处理 → 1已处理 / 2已驳回。
 *
 * <p>受理的「处理结果」会真的联动一次内容处置（下架/禁言/封禁），
 * 所以这里必须防重复：已处理的工单再点一次直接报错，
 * 否则一次误点会把作者禁言两次、帖子状态来回翻。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 处理结果常量，与 t_report_ticket.handle_result 的值域一致 */
    public static final String RESULT_OFF_SHELF = "下架内容";
    public static final String RESULT_MUTE = "禁言作者";
    public static final String RESULT_BAN = "封禁作者";
    public static final String RESULT_NO_VIOLATION = "无违规";
    public static final String RESULT_OTHER = "其他";

    private final ReportTicketMapper reportTicketMapper;
    private final NotificationClient notificationClient;
    private final ContentService contentService;
    private final UserManageService userManageService;
    private final DataScopeResolver dataScopeResolver;

    public R.Page<ReportTicket> pageTickets(Integer status, String targetType, String keyword,
                                           LocalDateTime start, LocalDateTime end, long page, long size) {
        DataScope ds = dataScopeResolver.resolve();
        List<Long> schoolIds = ds.isAll() ? null : ds.getSchoolIds();
        IPage<ReportTicket> p = reportTicketMapper.selectPage(new Page<>(page, size),
                Wrappers.<ReportTicket>lambdaQuery()
                        .eq(status != null, ReportTicket::getStatus, status)
                        .eq(targetType != null && !targetType.isBlank(), ReportTicket::getTargetType, targetType)
                        .like(keyword != null && !keyword.isBlank(), ReportTicket::getTicketNo, keyword)
                        .ge(start != null, ReportTicket::getCreatedAt, start)
                        .le(end != null, ReportTicket::getCreatedAt, end)
                        .in(schoolIds != null && !schoolIds.isEmpty(), ReportTicket::getSchoolId, schoolIds)
                        .apply(schoolIds != null && schoolIds.isEmpty(), "1 = 2")
                        .orderByAsc(ReportTicket::getStatus)
                        .orderByDesc(ReportTicket::getCreatedAt));
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    public ReportTicket ticketDetail(Long id) {
        ReportTicket ticket = reportTicketMapper.selectById(id);
        if (ticket == null) {
            throw BizException.notFound("工单不存在");
        }
        assertInScope(ticket.getSchoolId());
        return ticket;
    }

    /**
     * 受理工单。
     *
     * @param req 处理结果见本类的 RESULT_* 常量；
     *            req.status 只作兼容保留，最终状态由 handleResult 推导
     *            （「无违规」= 驳回，其余 = 已处理）
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, ReportHandleReq req) {
        ReportTicket ticket = ticketDetail(id);
        if (!Integer.valueOf(0).equals(ticket.getStatus())) {
            throw BizException.of("工单已处理，不能重复受理");
        }
        String result = req.getHandleResult();
        String remark = req.getHandleRemark();
        if (result == null || result.isBlank()) {
            throw BizException.of("处理结果不能为空");
        }

        String targetType = ticket.getTargetType();
        Long targetId = ticket.getTargetId();
        switch (result) {
            case RESULT_OFF_SHELF -> hideTarget(targetType, targetId, remark);
            case RESULT_MUTE -> {
                Long author = authorOf(targetType, targetId);
                userManageService.mute(author, 1, 1440, ticket.getReasonType(),
                        "举报工单 " + ticket.getTicketNo() + "：" + remark);
            }
            case RESULT_BAN -> {
                Long author = authorOf(targetType, targetId);
                userManageService.ban(author, ticket.getReasonType(),
                        "举报工单 " + ticket.getTicketNo() + "：" + remark);
            }
            case RESULT_NO_VIOLATION, RESULT_OTHER -> {
                // 仅记录结论，不动内容
            }
            default -> throw BizException.of("不支持的处理结果：" + result);
        }

        reportTicketMapper.update(null, Wrappers.<ReportTicket>lambdaUpdate()
                .eq(ReportTicket::getId, id)
                .set(ReportTicket::getStatus, RESULT_NO_VIOLATION.equals(result) ? 2 : 1)
                .set(ReportTicket::getHandleResult, result)
                .set(ReportTicket::getHandleRemark, remark)
                .set(ReportTicket::getHandlerId, AdminContext.adminId())
                .set(ReportTicket::getHandlerName, AdminContext.username())
                .set(ReportTicket::getHandledAt, LocalDateTime.now()));

        notifyReporter(ticket, result, remark);
    }

    /**
     * 把受理结论回执给举报人。
     *
     * <p>举报是 UGC 平台唯一的「用户替平台发现问题」的通道：
     * 用户举报后如果永远收不到回音，二次举报率会掉到接近 0，
     * 而且「处置申诉闭环」本身也是内容安全审核要看的能力项。
     *
     * <p>刻意放在事务方法的最末尾且完全 try/catch：
     * 通知是锦上添花，campus-message 抖动绝不能让已生效的处理回滚，
     * 也不能把 Feign 的耗时算进本事务。
     */
    private void notifyReporter(ReportTicket ticket, String result, String remark) {
        Long reporter = ticket.getReporterId();
        if (reporter == null) {
            return;
        }
        try {
            NotifyReq req = new NotifyReq();
            req.setType("SYSTEM");
            req.setReceiverId(reporter);
            req.setTargetType(ticket.getTargetType());
            req.setTargetId(ticket.getTargetId());
            req.setContentSnapshot("你提交的举报（工单 " + ticket.getTicketNo() + "）已处理：" + result
                    + (remark == null || remark.isBlank() ? "" : "；" + remark));
            notificationClient.send(req);
        } catch (Exception e) {
            log.warn("[admin] 举报回执通知发送失败，已忽略：ticket={}, {}", ticket.getTicketNo(), e.getMessage());
        }
    }

    /** 按被举报对象类型执行隐藏，评论走删除 */
    private void hideTarget(String targetType, Long targetId, String remark) {
        switch (targetType) {
            case "POST" -> contentService.setPostStatus(targetId, 0, "举报成立", remark);
            case "WALL" -> contentService.setWallStatus(targetId, 0, "举报成立", remark);
            case "GOODS" -> contentService.setGoodsStatus(targetId, 0, "举报成立", remark);
            case "COMMENT" -> throw BizException.of("评论类举报请直接删除评论，" +
                    "工单受理暂不联动（需先确认评论归属类型）");
            default -> throw BizException.of("该类型不支持联动处置：" + targetType);
        }
    }

    /** 取被举报内容的作者，用于禁言/封禁联动 */
    private Long authorOf(String targetType, Long targetId) {
        return switch (targetType) {
            case "POST" -> contentService.postDetail(targetId).getAuthorId();
            case "WALL" -> contentService.wallDetail(targetId).getRealUserId();
            case "GOODS" -> contentService.goodsDetail(targetId).getSellerId();
            case "USER" -> targetId;
            default -> throw BizException.of("该类型没有作者可处置：" + targetType);
        };
    }

    private void assertInScope(Long schoolId) {
        DataScope ds = dataScopeResolver.resolve();
        if (ds.isAll()) {
            return;
        }
        if (schoolId == null || !ds.getSchoolIds().contains(schoolId)) {
            throw BizException.forbidden("该工单不在你的管辖范围内");
        }
    }
}
