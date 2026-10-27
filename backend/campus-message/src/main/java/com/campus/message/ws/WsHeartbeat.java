package com.campus.message.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 心跳（30 秒一轮）。
 *
 * <p>小程序<b>不支持协议级 ping/pong 帧</b>，只能走业务层消息：
 * 服务端发 {@code {"type":"ping"}}，客户端回 {@code {"type":"pong"}}；
 * 超过 60 秒没收到 pong 判定死连接，关闭并从三个索引里摘除。
 *
 * <p>为什么必须做：移动网络切换、地铁进隧道、用户直接杀进程，都不会发 FIN。
 * 不做心跳，这些连接会一直挂在 {@link WsSessionRegistry} 里，
 * 表现为「内存只增不减 + 广播时往空气发消息」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsHeartbeat {

    /** 心跳间隔 */
    private static final long PING_INTERVAL_MS = 30_000L;
    /** 超过这个时间没 pong 就判定死连接（2 个心跳周期） */
    private static final long DEAD_MS = 65_000L;

    private final WsSessionRegistry registry;
    private final WsBroadcaster broadcaster;

    @Scheduled(initialDelay = 30_000L, fixedDelay = PING_INTERVAL_MS)
    public void ping() {
        long now = System.currentTimeMillis();
        List<WsSession> dead = new ArrayList<>();
        for (WsSession s : registry.all()) {
            if (!s.isOpen()) {
                dead.add(s);
                continue;
            }
            if (now - s.getLastPongAt() > DEAD_MS) {
                dead.add(s);
                continue;
            }
            s.send(broadcaster.json(GroupWsHandler.pingFrame()));
        }
        for (WsSession s : dead) {
            s.close();
            // 统一走 unregister 清理（三索引一起摘）
            registry.unregister(s.getSession());
        }
        if (!dead.isEmpty()) {
            log.info("[ws] 心跳清理死连接 {} 条，本机剩余 {}", dead.size(), registry.size());
        }
    }
}
