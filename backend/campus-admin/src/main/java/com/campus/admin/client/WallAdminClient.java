package com.campus.admin.client;

import com.campus.admin.dto.AdminActionReq;
import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 后台 → campus-wall 的处置通道：隐藏/恢复表白墙帖子。
 *
 * <p>隐藏不会抹掉 user_id，表白墙的匿名只是展示层的约定——
 * 后台处置后作者本人仍可在小程序里看到自己发的内容被下架。
 */
@FeignClient(name = "campus-wall", contextId = "adminWallClient", path = "/internal/admin/wall")
public interface WallAdminClient {

    @PostMapping("/{id}/status")
    R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody AdminActionReq req);

    /** 删除评论，服务端负责回写 t_wall_post.comment_count */
    @DeleteMapping("/comments/{id}")
    R<Void> deleteComment(@PathVariable("id") Long id);
}
