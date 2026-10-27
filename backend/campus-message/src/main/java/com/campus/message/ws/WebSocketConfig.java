package com.campus.message.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册 WebSocket 端点：{@code /ws}。
 *
 * <p>网关把 {@code /api/ws/**} 剥一层前缀后转发到这里，所以后端路径是 {@code /ws}。
 *
 * <p>关于 {@code setAllowedOriginPatterns("*")}：小程序发起 WebSocket 不带 Origin 头，
 * 而浏览器的 Origin 校验对小程序没有意义。<b>安全性不靠 Origin 兜底，靠握手时的
 * 一次性 ticket 校验</b>（见 {@link WsHandshakeInterceptor}）——
 * 没有有效 ticket 的连接在 beforeHandshake 阶段就被拒绝，连 handler 都进不来。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final GroupWsHandler groupWsHandler;
    private final WsHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(groupWsHandler, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
