package com.campus.message.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 群广播：把一条新消息推给「本机上订阅了这个群」的所有连接。
 *
 * <p><b>单实例实现。</b>本项目当前 campus-message 是单实例（且项目未引入 Redis），
 * 用进程内索引就够。多实例必须换成 Redis Pub/Sub 或任意消息中间件：
 * A 实例收到消息 → PUBLISH → 各实例订阅后各自推本机连接；
 * 否则「你连在 A、消息从 B 发出」时两边都收不到，而这个问题
 * <b>在单机测试时根本发现不了</b>，上线才暴露。
 * 替换时只需要改这个类的 {@link #broadcast}，业务侧一行不动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsBroadcaster {

    private final WsSessionRegistry registry;
    private final ObjectMapper objectMapper;

    /**
     * 推给某个群的所有本机在线成员。
     *
     * @param frame 完整下行帧，如 {@code {"type":"msg","message":{...}}}
     */
    public void broadcast(Long groupId, Map<String, Object> frame) {
        Collection<WsSession> sessions = registry.sessionsOfGroup(groupId);
        if (sessions.isEmpty()) {
            return;   // 本机没人，直接返回 —— 绝大多数情况
        }
        String text = json(frame);
        int ok = 0;
        for (WsSession s : sessions) {
            if (s.send(text)) {
                ok++;
            }
        }
        log.debug("[ws] 广播 group={} 命中连接={} 成功={}", groupId, sessions.size(), ok);
    }

    /** 给单个用户的所有连接推一帧（禁言 / 被踢 / 被设为管理员时用） */
    public void sendToUser(Long userId, Map<String, Object> frame) {
        Collection<WsSession> sessions = registry.sessionsOfUser(userId);
        if (sessions.isEmpty()) {
            return;
        }
        String text = json(frame);
        for (WsSession s : sessions) {
            s.send(text);
        }
    }

    /** 统一的序列化出口（心跳也要用，避免各处自己 new ObjectMapper） */
    public String json(Map<String, Object> frame) {
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (Exception e) {
            log.warn("[ws] 下行帧序列化失败: {}", e.getMessage());
            return "{\"type\":\"error\",\"code\":500,\"msg\":\"序列化失败\"}";
        }
    }

    /** 构造一条业务帧（统一出口，避免各处手写 Map 拼错字段名） */
    public static Map<String, Object> frame(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        return m;
    }

    public static Map<String, Object> frame(String type, String key, Object value) {
        Map<String, Object> m = frame(type);
        m.put(key, value);
        return m;
    }
}
