package com.campus.message.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.message.entity.Notification;
import com.campus.message.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知读取接口（前端用，走网关 /api/messages/notifications/**）。
 * Feign 内部写入入口见 {@link InternalNotificationController}。
 */
@RestController
@RequestMapping("/messages/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 未读统计（前端消息中心 tab 顶部 badge）。
     * 返回：{all, like, follow, mention, commentPost, commentWall, system}
     */
    @GetMapping("/unread")
    public R<Map<String, Integer>> unread() {
        Long uid = UserContext.requireUserId();
        NotificationService.UnreadStats s = notificationService.unreadStats(uid);
        return R.ok(Map.of(
                "all", s.all,
                "like", s.like,
                "follow", s.follow,
                "mention", s.mention,
                "commentPost", s.commentPost,
                "commentWall", s.commentWall,
                "system", s.system
        ));
    }

    /**
     * 通知列表，按 type 过滤（点赞 / 粉丝 / @ / 官方）。
     * type 取值：LIKE_GOODS / LIKE_POST / LIKE_WALL（赞类合并统计）、
     * NEW_FOLLOWER（粉丝）、MENTION + COMMENT_POST + COMMENT_WALL（@我 + 评论类合并）、SYSTEM（官方）。
     * 传 null 或空 = 全部。
     */
    @GetMapping
    public R<R.Page<Notification>> list(@RequestParam(required = false) String type,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size) {
        Long uid = UserContext.requireUserId();
        return R.ok(notificationService.list(uid, type, page, size));
    }

    /** 标记某类通知已读 */
    @PostMapping("/read/{type}")
    public R<Integer> markRead(@PathVariable String type) {
        Long uid = UserContext.requireUserId();
        return R.ok(notificationService.markReadByType(uid, type));
    }

    /** 全部标记已读 */
    @PostMapping("/read-all")
    public R<Integer> markAllRead() {
        Long uid = UserContext.requireUserId();
        return R.ok(notificationService.markAllRead(uid));
    }
}
