package com.campus.post.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.post.dto.PostReq;
import com.campus.post.entity.Channel;
import com.campus.post.entity.Post;
import com.campus.post.entity.PostComment;
import com.campus.post.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** 频道列表（全平台频道 + 本校频道） */
    @GetMapping("/channels")
    public R<List<Channel>> channels(@RequestParam(required = false, name = "schoolId") Long schoolId) {
        Long scope = schoolId != null ? schoolId : UserContext.schoolId();
        return R.ok(postService.channels(scope));
    }

    /**
     * 帖子分页。
     * scope=school（默认）：本校帖子；scope=city：city 指定城市（缺省取用户学校所在市）下所有高校的帖子。
     * sort=hot：按点赞数倒序（首页「最热帖子」用）。
     * userId：个人主页按作者过滤，只返回该作者 status=1 的帖子（忽略 school/city 范围）。
     */
    @GetMapping("/posts")
    public R<R.Page<Post>> page(@RequestParam(required = false, name = "channelId") Long channelId,
                                @RequestParam(required = false, name = "schoolId") Long schoolId,
                                @RequestParam(required = false, name = "scope") String scope,
                                @RequestParam(required = false, name = "city") String city,
                                @RequestParam(required = false, name = "userId") Long userId,
                                @RequestParam(defaultValue = "1", name = "page") long page,
                                @RequestParam(defaultValue = "10", name = "size") long size,
                                @RequestParam(required = false, name = "sort") String sort) {
        // 个人主页：按作者过滤，跳过 school/city 范围逻辑
        if (userId != null) {
            return R.ok(postService.pageByUser(userId, page, size, UserContext.userId()));
        }
        Long schoolScope = schoolId != null ? schoolId : UserContext.schoolId();
        if ("city".equalsIgnoreCase(scope)) {
            String targetCity = city != null && !city.isBlank() ? city : postService.cityOfSchool(schoolScope);
            return R.ok(postService.pageByCity(channelId, targetCity, page, size, UserContext.userId()));
        }
        return R.ok(postService.page(channelId, schoolScope, page, size, UserContext.userId(), sort));
    }

    @PostMapping("/posts")
    public R<Long> publish(@RequestBody @Valid PostReq req) {
        return R.ok(postService.publish(req));
    }

    @GetMapping("/posts/{id}")
    public R<Post> detail(@PathVariable("id") Long id) {
        return R.ok(postService.detail(id, UserContext.userId()));
    }

    @DeleteMapping("/posts/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        postService.delete(id);
        return R.ok();
    }

    /** 编辑自己的帖子 */
    @PutMapping("/posts/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody @Valid PostReq req) {
        postService.update(id, req);
        return R.ok();
    }

    @PostMapping("/posts/{id}/like")
    public R<Boolean> like(@PathVariable("id") Long id) {
        return R.ok(postService.toggleLike(id, UserContext.requireUserId()));
    }

    @GetMapping("/posts/{id}/comments")
    public R<List<PostComment>> comments(@PathVariable("id") Long id) {
        return R.ok(postService.comments(id, UserContext.userId()));
    }

    @PostMapping("/posts/{id}/comments")
    public R<Long> comment(@PathVariable("id") Long id, @RequestBody @Valid ContentReq req) {
        return R.ok(postService.addComment(id, req.getContent(), req.getImages(), req.getParentId(), req.getMentionUserIds()));
    }

    /** 删除自己的评论 */
    @DeleteMapping("/posts/{postId}/comments/{id}")
    public R<Void> deleteComment(@PathVariable("postId") Long postId, @PathVariable("id") Long id) {
        postService.deleteComment(postId, id);
        return R.ok();
    }

    /**
     * 点赞 / 取消点赞一条评论（含楼中楼回复）。
     * 返回值：true=已点赞，false=已取消。
     */
    @PostMapping("/posts/{postId}/comments/{id}/like")
    public R<Boolean> likeComment(@PathVariable("postId") Long postId, @PathVariable("id") Long id) {
        return R.ok(postService.toggleCommentLike(postId, id, UserContext.requireUserId()));
    }

    @Data
    public static class ContentReq {

        @Size(max = 500, message = "评论不超过 500 字")
        private String content;

        /** 评论配图，最多 3 张 */
        @Size(max = 3, message = "评论最多 3 张图")
        private List<String> images;

        /** 回复的父评论 id，空 / 0 表示直接评论 */
        private Long parentId;

        /** 评论中 @ 的用户 id 列表，后端强校验必须是评论者已关注的人 */
        private List<Long> mentionUserIds;
    }
}
