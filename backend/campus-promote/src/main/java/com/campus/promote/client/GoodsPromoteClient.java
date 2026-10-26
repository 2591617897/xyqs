package com.campus.promote.client;

import com.campus.common.api.R;
import com.campus.promote.dto.PromoteApplyReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * campus-goods 内部接口：查内容归属 + 回写推广分。
 *
 * <p>promote 服务不持有内容表，归属校验和分数落库都通过它完成，
 * 避免跨服务直接写别人的表。
 */
@FeignClient(name = "campus-goods", path = "/internal/goods")
public interface GoodsPromoteClient {

    @GetMapping("/owner")
    R<Long> owner(@RequestParam("id") Long id);

    @PostMapping("/promote/apply")
    R<Void> applyPromote(@RequestBody PromoteApplyReq req);
}
