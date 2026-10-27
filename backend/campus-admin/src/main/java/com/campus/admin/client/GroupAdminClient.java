package com.campus.admin.client;

import com.campus.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 后台 → campus-message 的校友群管理通道。
 *
 * <p>返回类型统一用 {@code R<Object>}：这一层只做「透传 + 权限 + 审计」，
 * 不重复定义一套与 message 侧一模一样的 DTO（两份 DTO 字段一旦漂移，
 * 后台就会静默显示空列，而且两边都不报错）。前端拿到的仍是标准
 * {@code {records,total,page,size,hasMore}} 分页结构。
 */
@FeignClient(name = "campus-message", contextId = "adminGroupClient", path = "/internal/admin/groups")
public interface GroupAdminClient {

    @GetMapping
    R<Object> page(@RequestParam(value = "keyword", required = false) String keyword,
                   @RequestParam(value = "status", required = false) Integer status,
                   @RequestParam("page") long page,
                   @RequestParam("size") long size);

    @GetMapping("/stats")
    R<Object> stats();

    @GetMapping("/{id}")
    R<Object> detail(@PathVariable("id") Long id);

    @PutMapping("/{id}")
    R<Void> update(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @GetMapping("/{id}/members")
    R<Object> members(@PathVariable("id") Long id,
                      @RequestParam(value = "keyword", required = false) String keyword,
                      @RequestParam("page") long page,
                      @RequestParam("size") long size);

    @PostMapping("/{id}/members/{userId}/role")
    R<Void> setRole(@PathVariable("id") Long id, @PathVariable("userId") Long userId,
                    @RequestBody Map<String, Object> body);

    @PostMapping("/{id}/mute")
    R<Void> mute(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PostMapping("/{id}/unmute")
    R<Object> unmute(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PostMapping("/{id}/kick")
    R<Void> kick(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @GetMapping("/{id}/mutes")
    R<Object> mutes(@PathVariable("id") Long id,
                    @RequestParam("activeOnly") boolean activeOnly,
                    @RequestParam("page") long page,
                    @RequestParam("size") long size);

    @GetMapping("/{id}/messages")
    R<Object> messages(@PathVariable("id") Long id,
                       @RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam("page") long page,
                       @RequestParam("size") long size);

    @PostMapping("/{id}/messages/{messageId}/delete")
    R<Void> deleteMessage(@PathVariable("id") Long id, @PathVariable("messageId") Long messageId);
}
