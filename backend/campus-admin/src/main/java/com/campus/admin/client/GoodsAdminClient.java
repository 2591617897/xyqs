package com.campus.admin.client;

import com.campus.admin.dto.AdminActionReq;
import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 后台 → campus-goods 的处置通道：强制下架/恢复在售。
 *
 * <p>与「已售出」区分：后台只能把 1在售 改成 0下架，
 * 反过来只恢复到 1，绝不把已售出的商品重新拉回在售。
 */
@FeignClient(name = "campus-goods", contextId = "adminGoodsClient", path = "/internal/admin/goods")
public interface GoodsAdminClient {

    @PostMapping("/{id}/status")
    R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody AdminActionReq req);

    /** 删除评论，服务端负责回写 t_goods.comment_count */
    @DeleteMapping("/comments/{id}")
    R<Void> deleteComment(@PathVariable("id") Long id);
}
