package com.campus.message.ws;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一条 WebSocket 连接的运行态。
 *
 * <p>为什么不直接持有 {@link WebSocketSession}：
 * <ol>
 *   <li>需要挂 userId / 已加入的群 / 最后心跳时间；</li>
 *   <li><b>发送必须加锁</b>。Spring 的 StandardWebSocketSession 不允许并发 sendMessage，
 *       同一个人被多条广播命中时会抛 IllegalStateException 直接断连；</li>
 *   <li>断开时要从三个索引里一一摘除（E-09：漏一个就是内存泄漏）。</li>
 * </ol>
 */
@Slf4j
public class WsSession {

    @Getter
    private final WebSocketSession session;

    @Getter
    private final Long userId;

    /** 这条连接当前订阅了哪些群（一个人可能同时在校友群和未来的子群） */
    private final Set<Long> groups = ConcurrentHashMap.newKeySet();

    /** 最后一次收到 pong 的时间，超过 60s 判定死连接 */
    @Getter
    private volatile long lastPongAt = System.currentTimeMillis();

    public WsSession(WebSocketSession session, Long userId) {
        this.session = session;
        this.userId = userId;
    }

    public String id() {
        return session.getId();
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    public void join(Long groupId) {
        groups.add(groupId);
    }

    public void leave(Long groupId) {
        groups.remove(groupId);
    }

    public boolean inGroup(Long groupId) {
        return groups.contains(groupId);
    }

    /** 已订阅的群快照（断开时遍历摘索引，必须拷贝，不能直接暴露内部集合） */
    public Set<Long> groupSnapshot() {
        return new HashSet<>(groups);
    }

    public void touch() {
        this.lastPongAt = System.currentTimeMillis();
    }

    /**
     * 发送一帧。已关闭的连接直接返回 false，不抛异常
     * —— 广播场景里一条死连接不该影响其他人收消息。
     */
    public boolean send(String text) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            synchronized (this) {
                session.sendMessage(new TextMessage(text));
            }
            return true;
        } catch (Exception e) {
            log.debug("[ws] 发送失败 session={} user={}: {}", id(), userId, e.getMessage());
            return false;
        }
    }

    public void close() {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {
            // 关不掉的连接会在心跳超时后被再次清理，这里不打断流程
        }
    }
}
