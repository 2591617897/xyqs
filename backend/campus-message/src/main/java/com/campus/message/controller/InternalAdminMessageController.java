package com.campus.message.controller;

import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.message.entity.Message;
import com.campus.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台管理专用内部接口（campus-admin → campus-message）：删除违规消息。
 *
 * <p>沿用消息服务既有的软删语义（deleted=1），与用户自助「撤回」写同一列，
 * 因此小程序侧不需要为新状态改造。不做物理删除：聊天记录是交易纠纷的证据，
 * 删了就再也取不回来。
 */
@Slf4j
@RestController
@RequestMapping("/internal/admin/messages")
@RequiredArgsConstructor
public class InternalAdminMessageController {

    private final MessageMapper messageMapper;

    @PostMapping("/{id}/delete")
    public R<Void> delete(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Message msg = messageMapper.selectById(id);
        if (msg == null) {
            throw BizException.notFound("消息不存在");
        }
        if (Integer.valueOf(1).equals(msg.getDeleted())) {
            // 幂等：重复删除直接成功，避免后台重试把成功操作报成失败
            return R.ok();
        }
        Message patch = new Message();
        patch.setId(id);
        patch.setDeleted(1);
        messageMapper.updateById(patch);
        log.info("[admin] 消息 {} 已被删除，原因：{}", id, body.get("reason"));
        return R.ok();
    }
}
