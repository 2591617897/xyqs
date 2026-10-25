package com.campus.admin.client;

import com.campus.admin.config.AdminContext;
import com.campus.admin.dto.AdminActionReq;
import com.campus.admin.mapper.FallbackWriteMapper;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 后台跨服务写门面：优先 Feign 调各服务的 /internal/admin/**，失败降级 admin 同库直写。
 *
 * <p>为什么要这么绕一层：
 * 内容的归属权在各自服务（帖子归 campus-post、禁言投影归 campus-user……），
 * 后台直接改库会绕过对方的缓存与业务校验；但后台又是管理端，
 * 对方服务临时不可用时必须还能处置违规内容，所以保留降级直写。
 *
 * <p>降级只用于「改状态」这类可重放的幂等操作，且打 WARN 日志——
 * 出现大量降级日志说明服务发现有问题，需要人介入，不能当常态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminWriteGateway {

    private final PostAdminClient postClient;
    private final WallAdminClient wallClient;
    private final GoodsAdminClient goodsClient;
    private final UserAdminClient userClient;
    private final MessageAdminClient messageClient;
    private final FallbackWriteMapper fallback;

    /** 帖子隐藏/恢复：status 0隐藏 1正常 */
    public void setPostStatus(Long id, Integer status, String reason) {
        AdminActionReq req = req(status, reason);
        try {
            R<Void> r = postClient.updateStatus(id, req);
            if (r != null && Integer.valueOf(0).equals(r.getCode())) {
                return;
            }
            throw BizException.of(r == null ? "帖子服务无响应" : r.getMsg());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[admin] 帖子服务不可用，降级直写 t_post：id={}, {}", id, e.getMessage());
            if (fallback.updatePostStatus(id, status) == 0) {
                throw BizException.notFound("帖子不存在");
            }
        }
    }

    /** 表白墙隐藏/恢复 */
    public void setWallStatus(Long id, Integer status, String reason) {
        AdminActionReq req = req(status, reason);
        try {
            R<Void> r = wallClient.updateStatus(id, req);
            if (r != null && Integer.valueOf(0).equals(r.getCode())) {
                return;
            }
            throw BizException.of(r == null ? "表白墙服务无响应" : r.getMsg());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[admin] 表白墙服务不可用，降级直写 t_wall_post：id={}, {}", id, e.getMessage());
            if (fallback.updateWallStatus(id, status) == 0) {
                throw BizException.notFound("内容不存在");
            }
        }
    }

    /** 商品强制下架/恢复在售 */
    public void setGoodsStatus(Long id, Integer status, String reason) {
        AdminActionReq req = req(status, reason);
        try {
            R<Void> r = goodsClient.updateStatus(id, req);
            if (r != null && Integer.valueOf(0).equals(r.getCode())) {
                return;
            }
            throw BizException.of(r == null ? "商品服务无响应" : r.getMsg());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[admin] 商品服务不可用，降级直写 t_goods：id={}, {}", id, e.getMessage());
            if (fallback.updateGoodsStatus(id, status) == 0) {
                throw BizException.notFound("商品不存在或已售出");
            }
        }
    }

    /** 封禁/解封用户 */
    public void setUserStatus(Long id, Integer status, String reason) {
        AdminActionReq req = req(status, reason);
        try {
            R<Void> r = userClient.updateStatus(id, req);
            if (r != null && Integer.valueOf(0).equals(r.getCode())) {
                return;
            }
            throw BizException.of(r == null ? "用户服务无响应" : r.getMsg());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[admin] 用户服务不可用，降级直写 t_user.status：id={}, {}", id, e.getMessage());
            if (fallback.updateUserStatus(id, status) == 0) {
                throw BizException.notFound("用户不存在");
            }
        }
    }

    /** 刷新禁言投影列 t_user.mute_until，until 为 null 表示解除 */
    public void setUserMuteUntil(Long id, LocalDateTime until, String reason) {
        Map<String, Object> body = new HashMap<>(4);
        body.put("muteUntil", until == null ? null : until.toString());
        body.put("reason", reason);
        body.put("operator", AdminContext.adminId());
        body.put("operatorName", AdminContext.username());
        try {
            R<Void> r = userClient.mute(id, body);
            if (r != null && Integer.valueOf(0).equals(r.getCode())) {
                return;
            }
            throw BizException.of(r == null ? "用户服务无响应" : r.getMsg());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[admin] 用户服务不可用，降级直写 t_user.mute_until：id={}, {}", id, e.getMessage());
            if (fallback.updateUserMuteUntil(id, until) == 0) {
                throw BizException.notFound("用户不存在");
            }
        }
    }

    /** 实名审核结论：2通过 3驳回 */
    public void setUserVerify(Long id, Integer verifyStatus, String reason) {
        Map<String, Object> body = new HashMap<>(4);
        body.put("verifyStatus", verifyStatus);
        body.put("reason", reason);
        body.put("operator", AdminContext.adminId());
        body.put("operatorName", AdminContext.username());
        try {
            R<Void> r = userClient.verify(id, body);
            if (r != null && Integer.valueOf(0).equals(r.getCode())) {
                return;
            }
            throw BizException.of(r == null ? "用户服务无响应" : r.getMsg());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[admin] 用户服务不可用，降级直写 t_user.verify_status：id={}, {}", id, e.getMessage());
            if (fallback.updateUserVerify(id, verifyStatus, verifyStatus == 3 ? reason : null) == 0) {
                throw BizException.notFound("用户不存在");
            }
        }
    }

    /** 删除违规消息（软删） */
    public void deleteMessage(Long id, String reason) {
        AdminActionReq req = req(0, reason);
        try {
            R<Void> r = messageClient.delete(id, req);
            if (r != null && Integer.valueOf(0).equals(r.getCode())) {
                return;
            }
            throw BizException.of(r == null ? "消息服务无响应" : r.getMsg());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[admin] 消息服务不可用，降级直写 t_message.deleted：id={}, {}", id, e.getMessage());
            if (fallback.deleteMessage(id) == 0) {
                throw BizException.notFound("消息不存在");
            }
        }
    }

    /**
     * 删除评论。三类评论归属三个服务，按 targetType 路由；
     * 降级时除了删评论本身，还要自己把 comment_count 减回去——
     * 正常路径下这件事由对方服务做，降级路径必须补齐，否则计数会虚高。
     */
    public void deleteComment(String targetType, Long id, Long targetId) {
        if ("P_COMMENT".equals(targetType)) {
            try {
                R<Void> r = postClient.deleteComment(id);
                if (r != null && Integer.valueOf(0).equals(r.getCode())) {
                    return;
                }
            } catch (Exception e) {
                log.warn("[admin] 帖子服务不可用，降级直删评论：id={}, {}", id, e.getMessage());
            }
            fallback.deletePostComment(id);
            if (targetId != null) {
                fallback.decPostCommentCount(targetId);
            }
            return;
        }
        if ("W_COMMENT".equals(targetType)) {
            try {
                R<Void> r = wallClient.deleteComment(id);
                if (r != null && Integer.valueOf(0).equals(r.getCode())) {
                    return;
                }
            } catch (Exception e) {
                log.warn("[admin] 表白墙服务不可用，降级直删评论：id={}, {}", id, e.getMessage());
            }
            fallback.deleteWallComment(id);
            if (targetId != null) {
                fallback.decWallCommentCount(targetId);
            }
            return;
        }
        if ("G_COMMENT".equals(targetType)) {
            try {
                R<Void> r = goodsClient.deleteComment(id);
                if (r != null && Integer.valueOf(0).equals(r.getCode())) {
                    return;
                }
            } catch (Exception e) {
                log.warn("[admin] 商品服务不可用，降级直删评论：id={}, {}", id, e.getMessage());
            }
            fallback.deleteGoodsComment(id);
            if (targetId != null) {
                fallback.decGoodsCommentCount(targetId);
            }
        }
    }

    private AdminActionReq req(Integer status, String reason) {
        AdminActionReq req = new AdminActionReq();
        req.setStatus(status);
        req.setReason(reason);
        req.setOperator(AdminContext.adminId());
        req.setOperatorName(AdminContext.username());
        return req;
    }
}
