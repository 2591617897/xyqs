package com.campus.wall.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.wall.entity.WallComment;
import com.campus.wall.entity.WallPost;
import com.campus.wall.mapper.WallCommentMapper;
import com.campus.wall.mapper.WallPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台管理专用内部接口（campus-admin → campus-wall）。
 *
 * <p>隐藏表白墙帖子不会抹掉 user_id：匿名只是前台展示层的约定，
 * 后台必须保留作者以便追溯，作者本人也能看到自己发的内容被下架。
 */
@Slf4j
@RestController
@RequestMapping("/internal/admin/wall")
@RequiredArgsConstructor
public class InternalAdminWallController {

    private final WallPostMapper wallPostMapper;
    private final WallCommentMapper wallCommentMapper;

    @PostMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("status");
        Integer status = raw == null ? null : Integer.valueOf(String.valueOf(raw));
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 非法，只允许 0隐藏 / 1正常");
        }
        WallPost patch = new WallPost();
        patch.setId(id);
        patch.setStatus(status);
        if (wallPostMapper.updateById(patch) == 0) {
            throw BizException.notFound("内容不存在");
        }
        log.info("[admin] 表白墙 {} -> status={}，原因：{}", id, status, body.get("reason"));
        return R.ok();
    }

    /** 删除单条墙评论并回写评论数（不连带删楼中楼回复，理由同帖子评论） */
    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable("id") Long id) {
        WallComment comment = wallCommentMapper.selectById(id);
        if (comment == null) {
            return R.ok();
        }
        wallCommentMapper.deleteById(id);
        wallPostMapper.update(null, Wrappers.<WallPost>lambdaUpdate()
                .eq(WallPost::getId, comment.getPostId())
                .setSql("comment_count = greatest(comment_count - 1, 0)"));
        log.info("[admin] 表白墙评论 {} 已删除", id);
        return R.ok();
    }
}
