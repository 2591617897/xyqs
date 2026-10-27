package com.campus.message.ws;

import com.campus.common.exception.BizException;
import com.campus.message.dto.GroupMsgVO;
import com.campus.message.dto.SendGroupMsgReq;
import com.campus.message.service.GroupService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 群聊 WebSocket 端点（后端路径 {@code /ws}，网关暴露为 {@code /api/ws}）。
 *
 * <p>协议见 {@code docs/校友群设计方案.md} §11.2：
 * <pre>
 * 上行：{type:"join"|"leave"|"msg"|"pong", groupId?, ...}
 * 下行：{type:"joined"|"msg"|"ping"|"error"|"kick"}
 * </pre>
 *
 * <p>一条连接可以 join 多个群。自己发的消息也走广播（不特判），
 * 前端按 {@code clientMsgId} 把本地乐观气泡<b>替换</b>成服务端真消息 ——
 * 这样只有一条代码路径，不会出现「自己发的和别人收到的格式不一致」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupWsHandler extends TextWebSocketHandler {

    private final WsSessionRegistry registry;
    private final GroupService groupService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(WsHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            // 理论上进不来（拦截器已拒），防御性关闭，避免出现匿名连接
            close(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        registry.register(session, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WsSession ws = registry.get(session.getId());
        if (ws == null) {
            return;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(ws, 400, "消息格式错误");
            return;
        }
        String type = node.path("type").asText("");
        switch (type) {
            case "pong" -> ws.touch();
            case "join" -> handleJoin(ws, node);
            case "leave" -> handleLeave(ws, node);
            case "msg" -> handleSend(ws, node);
            default -> sendError(ws, 400, "未知消息类型：" + type);
        }
    }

    private void handleJoin(WsSession ws, JsonNode node) {
        Long groupId = parseId(node.path("groupId").asText(null));
        if (groupId == null) {
            sendError(ws, 400, "groupId 无效");
            return;
        }
        // 订阅前必须校验成员身份：否则任何人 join 一下就能收到任意群的实时消息
        if (!groupService.isActiveMember(groupId, ws.getUserId())) {
            sendError(ws, 403, "你不在这个群里");
            return;
        }
        registry.join(ws, groupId);
        ws.send(json(WsBroadcaster.frame("joined", "groupId", String.valueOf(groupId))));
    }

    private void handleLeave(WsSession ws, JsonNode node) {
        Long groupId = parseId(node.path("groupId").asText(null));
        if (groupId != null) {
            registry.leave(ws, groupId);
        }
    }

    private void handleSend(WsSession ws, JsonNode node) {
        Long groupId = parseId(node.path("groupId").asText(null));
        if (groupId == null) {
            sendError(ws, 400, "groupId 无效");
            return;
        }
        SendGroupMsgReq req = new SendGroupMsgReq();
        // 用 msgType 而不是 type：外层 type 已被协议占用（"msg"）
        req.setType(node.path("msgType").asText("text"));
        req.setContent(node.path("content").asText(""));
        req.setClientMsgId(node.path("clientMsgId").asText(""));
        req.setAtAll(node.path("atAll").asBoolean(false));
        List<String> atIds = new ArrayList<>();
        JsonNode atNode = node.path("atUserIds");
        if (atNode.isArray()) {
            atNode.forEach(it -> atIds.add(it.asText()));
        }
        req.setAtUserIds(atIds);

        try {
            GroupMsgVO vo = groupService.send(groupId, ws.getUserId(), req);
            // 消息本身由广播统一推给全群（含发送者），这里不回包，避免前端收两次
            log.debug("[ws] 发送成功 group={} msg={}", groupId, vo.getId());
        } catch (BizException e) {
            sendError(ws, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("[ws] 发送异常 group={} user={}: {}", groupId, ws.getUserId(), e.getMessage());
            sendError(ws, 500, "发送失败，请重试");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 三个索引一起摘（byId / byGroup / byUser），漏一个就是内存泄漏
        registry.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("[ws] 传输异常 session={}: {}", session.getId(), exception.getMessage());
        registry.unregister(session);
        close(session, CloseStatus.SERVER_ERROR);
    }

    private void sendError(WsSession ws, int code, String msg) {
        ws.send(json(errorFrame(code, msg)));
    }

    private String json(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"code\":500,\"msg\":\"序列化失败\"}";
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception ignored) {
            // 关不掉的连接会在心跳超时后被再次清理
        }
    }

    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 下行 error 帧（前端按 type 分发，必须带 type） */
    static Map<String, Object> errorFrame(int code, String msg) {
        Map<String, Object> m = WsBroadcaster.frame("error");
        m.put("code", code);
        m.put("msg", msg == null ? "操作失败" : msg);
        return m;
    }

    /** 心跳帧，由 WsHeartbeat 调用 */
    static Map<String, Object> pingFrame() {
        return WsBroadcaster.frame("ping");
    }
}
