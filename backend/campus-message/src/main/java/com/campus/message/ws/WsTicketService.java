package com.campus.message.ws;

import com.campus.message.dto.WsTicketVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 握手用的一次性票据。
 *
 * <p><b>为什么不直接把 JWT 放 URL：</b>小程序 {@code wx.connectSocket} 无法可靠
 * 自定义请求头，只能把凭证放进 query；而 Nginx 的 access_log 会记录完整 URL，
 * 长效 JWT（72 小时）一旦落进日志就等于泄露，拿到就能冒充用户。
 *
 * <p><b>为什么不用 Redis 存：</b>本项目没有 Redis（服务端也没部署）。这里用进程内
 * 一次性票据：随机 32 字节、60 秒过期、{@link #consume} 取即删（原子 remove），
 * 泄露了也无害 —— 60 秒后失效，且用一次就废。多实例部署时需换成 Redis
 * {@code SET NX EX 60} + {@code GETDEL}，否则跨实例握手会失败。
 */
@Slf4j
@Service
public class WsTicketService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private record Ticket(Long userId, long expireAt) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    /** 票据有效期（秒） */
    @Value("${campus.group.ws-ticket-seconds:60}")
    private int ttlSeconds;

    /** 对外连接地址前缀，如 wss://campus-api.trkcwl.com/api/ws；留空则前端用 SERVER 自己拼 */
    @Value("${campus.group.ws-url:}")
    private String wsUrl;

    public WsTicketVO issue(Long userId) {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        long expireAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        tickets.put(ticket, new Ticket(userId, expireAt));

        WsTicketVO vo = new WsTicketVO();
        vo.setTicket(ticket);
        vo.setExpiresIn(ttlSeconds);
        vo.setUrl(wsUrl == null || wsUrl.isBlank()
                ? ""
                : wsUrl + "?ticket=" + ticket);
        return vo;
    }

    /**
     * 消费票据：<b>取即删</b>，同一张票无法用第二次。
     *
     * @return 票据对应的 userId；不存在 / 已用 / 过期返回 null（调用方直接拒绝握手）
     */
    public Long consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        Ticket t = tickets.remove(ticket);
        if (t == null) {
            return null;
        }
        if (t.expireAt() < System.currentTimeMillis()) {
            return null;
        }
        return t.userId();
    }

    /** 清掉过期票据，防止 Map 无界增长 */
    @Scheduled(initialDelay = 60_000L, fixedDelay = 300_000L)
    public void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Ticket>> it = tickets.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expireAt() < now) {
                it.remove();
            }
        }
    }
}
