package com.campus.auth.service;

import com.campus.auth.entity.ContentAuditTrail;
import com.campus.auth.mapper.ContentAuditTrailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 处置流水（t_content_audit）落库的唯一出口。
 *
 * <p>本地敏感词、微信内容安全、图片回调都要写这条流水，抽出来是为了
 * 保证 source / action / target_id 的语义<b>全仓一致</b>——
 * 之前它们是各写一个私有方法，很容易出现「同一个 FAIL 有的记 auratus 有的不记」。
 *
 * <p>落库失败只打日志：拦截动作本身已经生效，
 * 不能因为记录失败反而让用户看到 500。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAuditTrailWriter {

    private final ContentAuditTrailMapper auditTrailMapper;

    /**
     * @param targetType 业务类型（SecurityGuard.BIZ_*），未知传 null 会落成 UNKNOWN
     * @param targetId   业务主键；<b>拦截时尚未落库时传 null，统一记 0</b>
     * @param authorId   提交者
     * @param content    原文或图片地址（内部会截断到 200）
     * @param action     REJECT / WARN
     * @param reason     处置原因，运营在后台看到的就是它
     */
    public void record(String targetType, Long targetId, Long authorId,
                       String content, String action, String reason) {
        try {
            ContentAuditTrail t = new ContentAuditTrail();
            t.setTargetType(targetType == null || targetType.isBlank() ? "UNKNOWN" : targetType);
            t.setTargetId(targetId == null ? 0L : targetId);
            t.setAuthorId(authorId);
            t.setContentBrief(abbreviate(content));
            t.setAction(action);
            t.setReasonType("违规内容");
            t.setReason(reason);
            t.setSource(SOURCE_SYSTEM);
            auditTrailMapper.insert(t);
        } catch (Exception e) {
            log.warn("[audit] 写处置流水失败：{}", e.getMessage());
        }
    }

    /** 来源：2 = 系统自动（与后台人工=1、举报=3 区分） */
    private static final int SOURCE_SYSTEM = 2;

    /** 与 t_content_audit.content_brief(500) 匹配，留出余量 */
    private static final int BRIEF_LEN = 200;

    private static String abbreviate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= BRIEF_LEN ? s : s.substring(0, BRIEF_LEN);
    }
}
