package com.campus.message.controller;

import com.campus.common.api.R;
import com.campus.message.dto.GroupConfigReq;
import com.campus.message.dto.GroupMemberVO;
import com.campus.message.dto.GroupMsgVO;
import com.campus.message.dto.GroupMuteReq;
import com.campus.message.dto.GroupMuteVO;
import com.campus.message.dto.GroupRow;
import com.campus.message.dto.GroupStatsVO;
import com.campus.message.service.GroupAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台群管理内部接口（campus-admin → 本服务）。
 *
 * <p>与管理消息的 {@code InternalAdminMessageController} 同一套路：
 * 权限点校验与操作审计都在 campus-admin 侧，这里只管数据。
 */
@Slf4j
@RestController
@RequestMapping("/internal/admin/groups")
@RequiredArgsConstructor
public class InternalAdminGroupController {

    private final GroupAdminService adminService;

    @GetMapping
    public R<R.Page<GroupRow>> page(@RequestParam(required = false, name = "keyword") String keyword,
                                    @RequestParam(required = false, name = "status") Integer status,
                                    @RequestParam(defaultValue = "1", name = "page") long page,
                                    @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(adminService.page(keyword, status, page, size));
    }

    @GetMapping("/stats")
    public R<GroupStatsVO> stats() {
        return R.ok(adminService.stats());
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable("id") Long id) {
        // 详情直接用 C 端 VO 的字段集合，但后台不需要「我的状态」，故只回群本身
        return R.ok(adminService.detail(id));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable("id") Long id, @RequestBody GroupConfigReq req) {
        adminService.updateConfig(id, req);
        return R.ok();
    }

    @GetMapping("/{id}/members")
    public R<R.Page<GroupMemberVO>> members(@PathVariable("id") Long id,
                                            @RequestParam(required = false, name = "keyword") String keyword,
                                            @RequestParam(defaultValue = "1", name = "page") long page,
                                            @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(adminService.members(id, keyword, page, size));
    }

    @PostMapping("/{id}/members/{userId}/role")
    public R<Void> setRole(@PathVariable("id") Long id, @PathVariable("userId") Long userId,
                           @RequestBody Map<String, Object> body) {
        Integer role = body.get("role") == null ? 0 : Integer.valueOf(String.valueOf(body.get("role")));
        adminService.setRole(id, userId, role);
        return R.ok();
    }

    @PostMapping("/{id}/mute")
    public R<Void> mute(@PathVariable("id") Long id, @RequestBody @Valid GroupMuteReq req) {
        adminService.mute(id, Long.valueOf(req.getUserId()), req.getHours(), req.getReason(), null);
        return R.ok();
    }

    @PostMapping("/{id}/unmute")
    public R<Map<String, Object>> unmute(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(String.valueOf(body.get("userId")));
        int n = adminService.unmute(id, userId);
        return R.ok(Map.of("released", n));
    }

    @PostMapping("/{id}/kick")
    public R<Void> kick(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(String.valueOf(body.get("userId")));
        adminService.kick(id, userId);
        return R.ok();
    }

    @GetMapping("/{id}/mutes")
    public R<R.Page<GroupMuteVO>> mutes(@PathVariable("id") Long id,
                                        @RequestParam(defaultValue = "false", name = "activeOnly") boolean activeOnly,
                                        @RequestParam(defaultValue = "1", name = "page") long page,
                                        @RequestParam(defaultValue = "10", name = "size") long size) {
        return R.ok(adminService.mutes(id, activeOnly, page, size));
    }

    @GetMapping("/{id}/messages")
    public R<R.Page<GroupMsgVO>> messages(@PathVariable("id") Long id,
                                          @RequestParam(required = false, name = "keyword") String keyword,
                                          @RequestParam(defaultValue = "1", name = "page") long page,
                                          @RequestParam(defaultValue = "20", name = "size") long size) {
        return R.ok(adminService.messages(id, keyword, page, size));
    }

    @PostMapping("/{id}/messages/{messageId}/delete")
    public R<Void> deleteMessage(@PathVariable("id") Long id, @PathVariable("messageId") Long messageId) {
        adminService.deleteMessage(id, messageId);
        return R.ok();
    }
}
