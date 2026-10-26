package com.campus.promote.client;

import com.campus.common.api.R;
import com.campus.promote.dto.PromoteApplyReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** campus-wall 内部接口：查内容归属 + 回写推广分。 */
@FeignClient(name = "campus-wall", path = "/internal/wall/posts")
public interface WallPromoteClient {

    @GetMapping("/owner")
    R<Long> owner(@RequestParam("id") Long id);

    @PostMapping("/promote/apply")
    R<Void> applyPromote(@RequestBody PromoteApplyReq req);
}
