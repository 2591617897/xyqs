package com.campus.message.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 握手鉴权：校验一次性 ticket，并把 userId 塞进 session attributes。
 *
 * <p>⚠️ <b>这条链路完全绕开网关的 JWT 校验</b>（WebSocket 握手不带 Authorization 头，
 * 网关的 {@code AuthGlobalFilter} 对 {@code /api/ws/**} 是放行的）。
 * 所以这里是唯一的安全边界：<b>ticket 校验失败必须拒绝握手</b>，
 * 不能改成「先建立连接再提示登录」——那样任何人裸连都能收到广播。
 */
@Slf4j
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    /** 握手属性 key，Handler 从这里取 userId */
    public static final String ATTR_USER_ID = "wsUserId";

    private final WsTicketService ticketService;

    public WsHandshakeInterceptor(WsTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("ticket");
        Long userId = ticketService.consume(ticket);
        if (userId == null) {
            log.info("[ws] 握手被拒：票据无效或已过期 uri={}", request.getURI().getPath());
            return false;
        }
        attributes.put(ATTR_USER_ID, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
