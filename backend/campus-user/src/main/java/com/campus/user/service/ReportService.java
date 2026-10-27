package com.campus.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.exception.BizException;
import com.campus.common.security.UserContext;
import com.campus.user.dto.ReportReq;
import com.campus.user.entity.ReportTicket;
import com.campus.user.entity.User;
import com.campus.user.mapper.ReportBriefMapper;
import com.campus.user.mapper.ReportTicketMapper;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportTicketMapper reportMapper;
    private final ReportBriefMapper briefMapper;
    private final UserMapper userMapper;

    /**
     * 提交举报 / 反馈。同一人对同一内容重复举报时，不另起工单，只累加 report_count。
     */
    public Long create(ReportReq req) {
        Long userId = UserContext.requireUserId();
        User user = userMapper.selectById(userId);
        String nickname = user == null ? null : user.getNickname();

        ReportTicket ticket = new ReportTicket();
        ticket.setTicketNo(genTicketNo());
        ticket.setTargetType(req.getTargetType());
        ticket.setTargetId(req.getTargetId());
        ticket.setTargetBrief(buildBrief(req.getTargetType(), req.getTargetId()));
        ticket.setReporterId(userId);
        ticket.setReporterName(nickname);
        ticket.setReasonType(req.getReasonType());
        ticket.setReasonDesc(req.getReasonDesc());
        ticket.setImages(req.getImages() == null ? null : String.join(",", req.getImages()));
        ticket.setReportCount(1);
        ticket.setStatus(0);

        try {
            reportMapper.insert(ticket);
        } catch (DuplicateKeyException e) {
            // uk_target_reporter 命中：累加次数，避免工单表被刷
            reportMapper.update(null, Wrappers.<ReportTicket>lambdaUpdate()
                    .eq(ReportTicket::getTargetType, req.getTargetType())
                    .eq(ReportTicket::getTargetId, req.getTargetId())
                    .eq(ReportTicket::getReporterId, userId)
                    .setSql("report_count = report_count + 1"));
            ReportTicket exist = reportMapper.selectOne(Wrappers.<ReportTicket>lambdaQuery()
                    .eq(ReportTicket::getTargetType, req.getTargetType())
                    .eq(ReportTicket::getTargetId, req.getTargetId())
                    .eq(ReportTicket::getReporterId, userId));
            return exist == null ? null : exist.getId();
        }
        return ticket.getId();
    }

    /**
     * 反查被举报对象的纯文本摘要，写进 target_brief 供后台列表直接查看。
     *
     * <p>t_report_ticket.target_brief 只有 varchar(500)，而帖子正文可能上千字，
     * 所以必须截断，否则 MySQL 报 Data too long 会让「提交举报」直接失败——
     * 宁可摘要被切短，也不能让举报发不出去。
     *
     * @return 摘要；取不到（内容已删 / FEEDBACK 无 targetId）时为 null
     */
    private String buildBrief(String targetType, Long targetId) {
        if (targetId == null) {
            return null;
        }
        String raw;
        switch (targetType) {
            case "POST" -> raw = briefMapper.post(targetId);
            case "WALL" -> raw = briefMapper.wall(targetId);
            case "GOODS" -> raw = briefMapper.goods(targetId);
            case "USER" -> raw = briefMapper.user(targetId);
            case "MESSAGE" -> raw = briefMapper.message(targetId);
            case "GROUP_MSG" -> raw = briefMapper.groupMsg(targetId);
            case "COMMENT" -> {
                // 工单只记了 targetId，不知归属哪张评论表，依次试探
                raw = briefMapper.postComment(targetId);
                if (raw == null) {
                    raw = briefMapper.wallComment(targetId);
                }
                if (raw == null) {
                    raw = briefMapper.goodsComment(targetId);
                }
            }
            default -> raw = null; // FEEDBACK 等无实体对象
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        raw = raw.replaceAll("\\s+", " ").trim();
        return raw.length() > 480 ? raw.substring(0, 480) + "…" : raw;
    }

    public List<ReportTicket> listMine() {
        Long userId = UserContext.requireUserId();
        return reportMapper.selectList(Wrappers.<ReportTicket>lambdaQuery()
                .eq(ReportTicket::getReporterId, userId)
                .orderByDesc(ReportTicket::getCreatedAt));
    }

    public ReportTicket getById(Long id) {
        Long userId = UserContext.requireUserId();
        ReportTicket ticket = reportMapper.selectById(id);
        if (ticket == null) {
            throw BizException.notFound("工单不存在");
        }
        if (ticket.getReporterId() != null && !ticket.getReporterId().equals(userId)) {
            throw BizException.forbidden("无权查看他人工单");
        }
        return ticket;
    }

    private String genTicketNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = new Random().nextInt(9000) + 1000;
        return "RP" + ts + rnd;
    }
}
