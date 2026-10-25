package com.campus.admin.client;

import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 让 campus-auth 立刻重载本地敏感词缓存。
 *
 * <p>auth 侧本身有定时刷新兜底（默认 60s），这里只是把「运营改完词想马上生效」
 * 的等待时间抹掉。所以它是<b>尽力而为</b>的调用：auth 没起、网络抖了都无所谓，
 * 顶多多等一个刷新周期，绝不能让后台的增删改词失败。
 */
@FeignClient(name = "campus-auth", contextId = "adminSecurityClient", path = "/internal/security")
public interface SecurityAdminClient {

    /** @return 重载后的词库条数 */
    @PostMapping("/words/reload")
    R<Integer> reloadWords();
}
