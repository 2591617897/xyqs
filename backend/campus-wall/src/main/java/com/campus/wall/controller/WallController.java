package com.campus.wall.controller;

import com.campus.common.api.R;
import com.campus.common.security.UserContext;
import com.campus.wall.dto.CommentReq;
import com.campus.wall.dto.WallPostReq;
import com.campus.wall.entity.WallComment;
import com.campus.wall.entity.WallPost;
import com.campus.wall.service.WallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/wall/posts")
@RequiredArgsConstructor
public class WallController {

    private final WallService wallService;

    /** 表白墙列表（未登录可浏览，登录后返回点赞/本人标记） */
    @GetMapping
    public R<R.Page<WallPost>> page(@RequestParam(required = false) Long schoolId,
                                    @RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size) {
        Long scope = schoolId != null ? schoolId : UserContext.schoolId();
        return R.ok(wallService.page(scope, UserContext.userId(), page, size));
    }

    @PostMapping
    public R<Long> publish(@RequestBody @Valid WallPostReq req) {
        return R.ok(wallService.publish(req));
    }

    @GetMapping("/{id}")
    public R<WallPost> detail(@PathVariable Long id) {
        return R.ok(wallService.detail(id, UserContext.userId()));
    }

    /** 编辑自己的表白 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid WallPostReq req) {
        wallService.update(id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        wallService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/like")
    public R<Boolean> like(@PathVariable Long id) {
        return R.ok(wallService.toggleLike(id, UserContext.requireUserId()));
    }

    @GetMapping("/{id}/comments")
    public R<List<WallComment>> comments(@PathVariable Long id) {
        return R.ok(wallService.comments(id, UserContext.userId()));
    }

    @PostMapping("/{id}/comments")
    public R<Long> comment(@PathVariable Long id, @RequestBody @Valid CommentReq req) {
        return R.ok(wallService.addComment(id, req));
    }

    /** 删除自己的评论 */
    @DeleteMapping("/{postId}/comments/{id}")
    public R<Void> deleteComment(@PathVariable Long postId, @PathVariable Long id) {
        wallService.deleteComment(postId, id);
        return R.ok();
    }
}
