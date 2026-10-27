package com.campus.common.client;

import com.campus.common.api.R;
import com.campus.common.client.dto.MediaCheckReq;
import com.campus.common.client.dto.MsgCheckReq;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 内容安全守卫：业务侧唯一的调用入口。
 *
 * <p>两类能力：
 * <ul>
 *   <li>{@link #checkText} —— 同步文本审核，命中违规直接 400，不落库；</li>
 *   <li>{@link #checkImages} —— 异步图片送审，只负责提交，结论由微信回调后落库。</li>
 * </ul>
 *
 * <p>语义约定：
 * <ul>
 *   <li>命中违规 → 抛 {@link BizException}(400)，调用方不必再判断；</li>
 *   <li>微信侧不可用（auth 宕机、网络异常、开关关闭）→ <b>记日志后放行</b>。
 *       审核是增强能力，绝不能变成「auth 挂了全站发不出帖」的单点故障。</li>
 * </ul>
 *
 * <p>用法（在落库语句之前一行）：
 * <pre>
 *   securityGuard.checkText(UserContext.requireUserId(), content, SecurityGuard.SCENE_FORUM, BIZ_WALL);
 *   securityGuard.checkImages(userId, BIZ_GOODS, null, req.getImages());
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
    /** 微信场景值：3 论坛（发帖、商品描述、私信） */
    public static final int SCENE_FORUM = 3;
    /** 微信场景值：4 社交日志 */
    public static final int SCENE_LOG = 4;

    /** 媒体类型：1 图片（微信 media_check_async 仅支持 1 图片 / 2 音频） */
    public static final int MEDIA_IMAGE = 1;

    /** 单条内容最多送审 9 张图，与各业务「最多 9 图」的上限一致 */
    private static final int MAX_MEDIA = 9;

    /** 业务类型：与 t_content_audit.target_type 取值保持一致 */
    public static final String BIZ_POST = "POST";
    public static final String BIZ_WALL = "WALL";
    public static final String BIZ_GOODS = "GOODS";
    public static final String BIZ_POST_COMMENT = "P_COMMENT";
    public static final String BIZ_WALL_COMMENT = "W_COMMENT";
    public static final String BIZ_GOODS_COMMENT = "G_COMMENT";
    public static final String BIZ_MESSAGE = "MESSAGE";
    public static final String BIZ_NICKNAME = "NICKNAME";
    /**
     * 校友群消息。
     *
     * <p>群聊是公开多对多场景，审核口径比私信更严：私信只影响两个人，
     * 群消息一万人可见。所以群消息必须走 {@code SCENE_FORUM}（论坛）而不是
     * {@code SCENE_LOG}，且落库前同步拦截（违规直接 400，不落库）。
     */
    public static final String BIZ_GROUP_MSG = "GROUP_MSG";

    /** 校园需求正文（标题 + 要求） */
    public static final String BIZ_DEMAND = "DEMAND";
    /** 需求回应留言：只在两个人之间可见，但会带联系方式，所以同样送审 */
    public static final String BIZ_DEMAND_REPLY = "DEMAND_REPLY";

    private final SecurityClient securityClient;

    /**
     * 校验文本，命中违规直接抛 400。
     *
     * @param userId 提交者 id
     * @param text   待校验文本（可为 null / 空，直接放行）
     * @param scene  场景值，取本类的 SCENE_* 常量
     */
    public void checkText(Long userId, String text, int scene) {
        checkText(userId, text, scene, null);
    }

    /**
     * 校验文本（带业务类型，违规事件会落到 t_content_audit 的 target_type 上）。
     *
     * @param bizType 取本类的 BIZ_* 常量，可为 null
     */
    public void checkText(Long userId, String text, int scene, String bizType) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            R<Void> r = securityClient.msgCheck(new MsgCheckReq(userId, text, scene, bizType));
            if (r != null && r.getCode() == 400) {
                throw BizException.of(r.getMsg() == null ? "内容涉嫌违规，请修改后再发布" : r.getMsg());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[security-guard] 内容安全校验不可用，本条放行：{}", e.getMessage());
        }
    }

    /**
     * 本地敏感词净化：<b>拿返回值去落库</b>，这是 level 2（替换）能生效的唯一途径。
     *
     * <p>与 {@link #checkText} 的分工：
     * <ul>
     *   <li>{@code purify} —— 本地词表，同步、快、不依赖凭据。按字段调用，
     *       命中 level 3 直接抛 400，命中 level 2 返回替换后的文本。</li>
     *   <li>{@code checkText} —— 微信审核，覆盖语义与隐含表达。整体调用一次即可。</li>
     * </ul>
     * 典型写法（先在字段上净化，再整体送微信）：
     * <pre>
     *   String title = securityGuard.purify(userId, req.getTitle(), BIZ_POST);
     *   String content = securityGuard.purify(userId, req.getContent(), BIZ_POST);
     *   securityGuard.checkText(userId, title + "\n" + content, SCENE_FORUM, BIZ_POST);
     *   post.setTitle(title); post.setContent(content);
     * </pre>
     *
     * <p>本方法与 checkText 一样遵循 fail-open：auth 不可用时原样返回，
     * 不会因为词典服务抖动就让内容发不出去。
     *
     * @param userId  提交者 id
     * @param text    待净化文本（null / 空则原样返回）
     * @param bizType 业务类型，取本类 BIZ_* 常量
     * @return 处理后的文本
     */
    public String purify(Long userId, String text, String bizType) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            R<String> r = securityClient.purify(new MsgCheckReq(userId, text, null, bizType));
            if (r != null && r.getCode() == 400) {
                throw BizException.of(r.getMsg() == null ? "内容包含不允许发布的敏感词" : r.getMsg());
            }
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[security-guard] 本地词净化不可用，按原文处理：{}", e.getMessage());
        }
        return text;
    }

    /**
     * 图片异步送审（不阻塞发布流程，也不会因为微信侧异常而让发布失败）。
     *
     * <p>注意这是<b>先发后审</b>：微信的媒体审核没有同步接口，结论通过回调返回。
     * 命中违规的结果会落到 t_media_check 与 t_content_audit，由运营在后台处置。
     *
     * @param userId  提交者 id
     * @param bizType 业务类型（BIZ_* 常量）
     * @param bizId   业务主键，发布前拿不到可传 null
     * @param urls    图片地址集合，非 http(s) 的会被过滤（本地上传前的临时路径没有意义）
     */
    public void checkImages(Long userId, String bizType, Long bizId, Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        List<String> valid = urls.stream()
                .filter(u -> u != null && !u.isBlank())
                .map(String::trim)
                .filter(u -> u.startsWith("http://") || u.startsWith("https://"))
                .distinct()
                .limit(MAX_MEDIA)
                .toList();
        if (valid.isEmpty()) {
            return;
        }
        try {
            R<Integer> r = securityClient.mediaCheck(
                    new MediaCheckReq(userId, bizType, bizId, valid, MEDIA_IMAGE));
            if (r != null && r.getCode() != 0) {
                log.warn("[security-guard] 图片送审未完全成功：code={} msg={}", r.getCode(), r.getMsg());
            }
        } catch (Exception e) {
            log.warn("[security-guard] 图片审核不可用，本次跳过：{}", e.getMessage());
        }
    }
}
