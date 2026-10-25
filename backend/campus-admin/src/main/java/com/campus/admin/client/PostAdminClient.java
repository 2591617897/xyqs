package com.campus.admin.client;

import com.campus.admin.dto.AdminActionReq;
import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 后台 → campus-post 的处置通道。
 *
 * <p>只做一件事：改帖子 status（1正常 0隐藏）。帖子的最终可见性永远由 campus-post 自己持有，
 * 后台不复制一份状态，避免两边不一致。调用失败由 AdminWriteGateway 降级为 admin 同库直写。
 */
@FeignClient(name = "campus-post", contextId = "adminPostClient", path = "/internal/admin/posts")
public interface PostAdminClient {

    @PostMapping("/{id}/status")
    R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody AdminActionReq req);

    /** 删除评论，服务端负责回写 t_post.comment_count */
    @DeleteMapping("/comments/{id}")
    R<Void> deleteComment(@PathVariable("id") Long id);
}
