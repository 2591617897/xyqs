package com.campus.post.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.post.entity.Post;
import com.campus.post.entity.PostComment;
import com.campus.post.mapper.PostCommentMapper;
import com.campus.post.mapper.PostMapper;
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
 * 后台管理专用内部接口（campus-admin → campus-post）。
 *
 * <p>/internal/** 不在网关路由里，只能由同一 Nacos 集群内的服务通过 Feign 直连，
 * 与既有的 /internal/posts/count 一致，无需额外鉴权。
 *
 * <p>这里只改 status（1正常 0隐藏）。后台不复制一份帖子状态到自己库，
 * 保证「帖子能不能被看到」永远只有 campus-post 一个真相。
 */
@Slf4j
@RestController
@RequestMapping("/internal/admin/posts")
@RequiredArgsConstructor
public class InternalAdminPostController {

    private final PostMapper postMapper;
    private final PostCommentMapper postCommentMapper;

    @PostMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Integer status = intOf(body.get("status"));
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 非法，只允许 0隐藏 / 1正常");
        }
        Post patch = new Post();
        patch.setId(id);
        patch.setStatus(status);
        if (postMapper.updateById(patch) == 0) {
            throw BizException.notFound("帖子不存在");
        }
        log.info("[admin] 帖子 {} -> status={}，原因：{}", id, status, body.get("reason"));
        return R.ok();
    }

    /**
     * 删除单条评论并回写帖子的评论数。
     *
     * <p>只删这一条：楼中楼回复不跟着删，否则一次违规会牵连整层正常讨论。
     * 计数用 greatest(...,0) 兜底，避免历史数据已经漂移时算出负数。
     */
    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable("id") Long id) {
        PostComment comment = postCommentMapper.selectById(id);
        if (comment == null) {
            // 幂等：重复删除按成功处理，后台重试不该报失败
            return R.ok();
        }
        postCommentMapper.deleteById(id);
        postMapper.update(null, Wrappers.<Post>lambdaUpdate()
                .eq(Post::getId, comment.getPostId())
                .setSql("comment_count = greatest(comment_count - 1, 0)"));
        log.info("[admin] 帖子评论 {} 已删除", id);
        return R.ok();
    }

    private Integer intOf(Object v) {
        return v == null ? null : Integer.valueOf(String.valueOf(v));
    }
}
