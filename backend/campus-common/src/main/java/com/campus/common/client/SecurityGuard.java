package com.campus.common.client;

import com.campus.common.api.R;
import com.campus.common.client.dto.MsgCheckReq;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 内容安全守卫：业务侧唯一的调用入口。
 *
 * 语义约定：
 * <ul>
 *   <li>命中违规 → 抛 {@link BizException}(400)，调用方不必再判断；</li>
 *   <li>微信侧不可用（auth 宕机、网络异常、开关关闭）→ <b>记日志后放行</b>。
 *       审核是增强能力，绝不能变成「auth 挂了全站发不出帖」的单点故障。</li>
 * </ul>
 *
 * 用法（在落库语句之前一行）：
 * <pre>
 *   securityGuard.checkText(UserContext.requireUserId(), content, SecurityGuard.SCENE_FORUM);
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityGuard {

    /** 微信场景值：1 资料 */
    public static final int SCENE_PROFILE = 1;
    /** 微信场景值：2 评论 */
    public static final int SCENE_COMMENT = 2;
    /** 微信场景值：3 论坛（发帖、商品描述） */
    public static final int SCENE_FORUM = 3;

    private final SecurityClient securityClient;

    /**
     * 校验文本，命中违规直接抛 400。
     *
     * @param userId 提交者 id
     * @param text   待校验文本（可为 null / 空，直接放行）
     * @param scene  场景值，取本类的 SCENE_* 常量
     */
    public void checkText(Long userId, String text, int scene) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            R<Void> r = securityClient.msgCheck(new MsgCheckReq(userId, text, scene));
            if (r != null && r.getCode() == 400) {
                throw BizException.of(r.getMsg() == null ? "内容涉嫌违规，请修改后再发布" : r.getMsg());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[security-guard] 内容安全校验不可用，本条放行：{}", e.getMessage());
        }
    }
}
