package com.campus.message.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本机连接索引（三个维度）。
 *
 * <pre>
 * byId   : sessionId → 连接       （断开时精确定位）
 * byGroup: groupId   → 本机该群连接（广播用：本机没这个群的连接就直接跳过）
 * byUser : userId    → 该用户全部连接（多端；禁言/踢人时要踢掉他所有连接）
 * </pre>
 *
 * <p>⚠️ 摘除必须三处一起做。漏了 byGroup 会一直往已关闭的连接发消息，
 * 漏了 byUser 会让该用户的内存常驻不释放 —— 页面反复进出后内存持续增长（E-09）。
 */
@Slf4j
@Component
public class WsSessionRegistry {

    private final Map<String, WsSession> byId = new ConcurrentHashMap<>();
    private final Map<Long, Set<WsSession>> byGroup = new ConcurrentHashMap<>();
    private final Map<Long, Set<WsSession>> byUser = new ConcurrentHashMap<>();

    public WsSession register(WebSocketSession session, Long userId) {
        WsSession ws = new WsSession(session, userId);
        byId.put(ws.id(), ws);
        byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(ws);
        log.info("[ws] 连接建立 user={} session={} 本机连接数={}", userId, ws.id(), byId.size());
        return ws;
    }

    public void unregister(WebSocketSession session) {
        WsSession ws = byId.remove(session.getId());
        if (ws == null) {
            return;
        }
        // ① byGroup：先拷贝再遍历，避免并发修改
        for (Long gid : ws.groupSnapshot()) {
            removeFromGroup(gid, ws);
        }
        // ② byUser
        Set<WsSession> users = byUser.get(ws.getUserId());
        if (users != null) {
            users.remove(ws);
            if (users.isEmpty()) {
                byUser.remove(ws.getUserId());
            }
        }
        // ③ byId 已在方法开头 remove
        log.info("[ws] 连接关闭 user={} session={} 本机连接数={}", ws.getUserId(), ws.id(), byId.size());
    }

    public WsSession get(String sessionId) {
        return byId.get(sessionId);
    }

    public void join(WsSession ws, Long groupId) {
        ws.join(groupId);
        byGroup.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(ws);
    }

    public void leave(WsSession ws, Long groupId) {
        ws.leave(groupId);
        removeFromGroup(groupId, ws);
    }

    private void removeFromGroup(Long groupId, WsSession ws) {
        Set<WsSession> set = byGroup.get(groupId);
        if (set != null) {
            set.remove(ws);
            if (set.isEmpty()) {
                byGroup.remove(groupId);
            }
        }
    }

    /** 本机订阅了该群的连接；没有则返回空集合（广播时直接跳过，绝大多数情况都是空） */
    public Collection<WsSession> sessionsOfGroup(Long groupId) {
        Set<WsSession> set = byGroup.get(groupId);
        return set == null ? List.of() : set;
    }

    public Collection<WsSession> sessionsOfUser(Long userId) {
        Set<WsSession> set = byUser.get(userId);
        return set == null ? List.of() : set;
    }

    public Collection<WsSession> all() {
        return new ArrayList<>(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
