package com.campus.admin.client;

import com.campus.admin.dto.AdminActionReq;
import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 后台 → campus-goods 的处置通道：需求广场强制下架 / 恢复 / 删除。
 *
 * <p>与商品同构：后台只能把 1进行中 改成 0下架，或把 0下架 恢复到 1进行中；
 * 不碰 2已完成（那是发布者自己关闭的需求）。
 */
@FeignClient(name = "campus-goods", contextId = "adminDemandClient", path = "/internal/admin/demands")
public interface DemandAdminClient {

    @PostMapping("/{id}/status")
    R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody AdminActionReq req);

    /**
     * 需求分类字典（type/id/name/icon）。
     * 分类白名单的唯一数据源在 campus-goods，后台不自己维护一份。
     */
    @GetMapping("/categories")
    R<List<Map<String, Object>>> categories();

    /** 删除需求，服务端负责级联清理回应 */
    @DeleteMapping("/{id}")
    R<Void> delete(@PathVariable("id") Long id);
}
