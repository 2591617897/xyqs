package com.campus.message.service;

import com.campus.common.api.R;
import com.campus.common.client.UserClient;
import com.campus.common.client.dto.UserBrief;
import com.campus.common.exception.BizException;
import com.campus.message.dto.ConversationVO;
import com.campus.message.dto.GoodsCard;
import com.campus.message.dto.MessageVO;
import com.campus.message.dto.PeerTag;
import com.campus.message.dto.SendMessageReq;
import com.campus.message.dto.StartConversationReq;
import com.campus.message.entity.Conversation;
import com.campus.message.entity.Message;
import com.campus.message.mapper.ConversationMapper;
import com.campus.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 私信与消息中心。
 * 会话成员固定为 (userA = 小 id, userB = 大 id)，运行时按当前登录用户判断「我 / 对端」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    /** 会话列表最多返回条数 */
    private static final int CONVERSATION_LIMIT = 100;
    /** 单个会话最多返回的聊天记录条数 */
    private static final int MESSAGE_LIMIT = 200;
    /** 会话列表摘要截断长度 */
    private static final int SUMMARY_LEN = 60;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserClient userClient;

    // ---------------- 会话列表 ----------------

    public List<ConversationVO> conversations(Long userId) {
        List<Conversation> list = conversationMapper.selectMine(userId, CONVERSATION_LIMIT);
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> peerIds = list.stream()
                .map(c -> peerOf(c, userId))
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, UserBrief> briefs = loadBriefs(peerIds);

        List<ConversationVO> vos = new ArrayList<>(list.size());
        for (Conversation c : list) {
            vos.add(toVO(c, userId, briefs, false));
        }
        return vos;
    }

    /** 消息中心顶部统计（未读 / 收到的赞 / 评论与回复） */
    public Map<String, Object> summary(Long userId) {
        Map<String, Object> m = new HashMap<>();
        m.put("unreadTotal", conversationMapper.countUnread(userId));
        m.put("likes", safeCount(() -> messageMapper.countReceivedLikes(userId)));
        m.put("mentions", safeCount(() -> messageMapper.countReceivedComments(userId)));
        // 本版本未实现关注关系，先返回 0，前端角标不展示
        m.put("followers", 0);
        m.put("notice", "市集");
        return m;
    }

    // ---------------- 会话详情 / 聊天 ----------------

    /**
     * 会话详情：返回聊天记录，并把对方发给我的消息标为已读。
     */
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO detail(Long conversationId, Long userId) {
        Conversation c = requireConversation(conversationId, userId);
        messageMapper.markRead(conversationId, userId);
        resetUnread(c, userId);
        ConversationVO vo = toVO(c, userId, loadBriefs(List.of(peerOf(c, userId))), true);
        vo.setMessages(messages(conversationId, userId));
        vo.setUnread(0);
        return vo;
    }

    /** 会话内的聊天记录（升序） */
    public List<MessageVO> messages(Long conversationId, Long userId) {
        List<Message> list = messageMapper.selectRecent(conversationId, MESSAGE_LIMIT);
        List<MessageVO> vos = new ArrayList<>(list.size());
        for (Message m : list) {
            MessageVO vo = new MessageVO();
            vo.setId(m.getId());
            vo.setFrom(userId.equals(m.getSenderId()) ? "me" : "peer");
            vo.setType(m.getType() == null ? "text" : m.getType());
            vo.setContent(m.getContent());
            vo.setCreatedAt(m.getCreatedAt());
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 发消息。会话不存在时按 (我, 对端) 自动建立。
     */
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO send(Long conversationId, Long userId, SendMessageReq req) {
        Conversation c = requireConversation(conversationId, userId);
        Long peerId = peerOf(c, userId);
        if (peerId == null || peerId <= 0) {
            throw new BizException("该会话无法回复");
        }
        String type = normalizeType(req.getType());
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new BizException("消息内容不能为空");
        }
        if (content.length() > 2000) {
            content = content.substring(0, 2000);
        }

        Message msg = new Message();
        msg.setConversationId(c.getId());
        msg.setSenderId(userId);
        msg.setReceiverId(peerId);
        msg.setType(type);
        msg.setContent(content);
        msg.setReadFlag(0);
        messageMapper.insert(msg);

        // 更新会话摘要，并给「对端」累加未读
        boolean meIsA = userId.equals(c.getUserA());
        conversationMapper.refreshAndIncrUnread(c.getId(), msg.getId(), summarize(content),
                msg.getCreatedAt() == null ? LocalDateTime.now() : msg.getCreatedAt(),
                meIsA ? 0 : 1, meIsA ? 1 : 0);

        c.setLastMsgId(msg.getId());
        c.setLastContent(summarize(content));
        c.setLastTime(msg.getCreatedAt());
        ConversationVO vo = toVO(c, userId, loadBriefs(List.of(peerId)), true);
        vo.setMessages(messages(conversationId, userId));
        vo.setUnread(0);
        return vo;
    }

    /** 把整个会话标为已读 */
    @Transactional(rollbackFor = Exception.class)
    public void read(Long conversationId, Long userId) {
        Conversation c = requireConversation(conversationId, userId);
        messageMapper.markRead(conversationId, userId);
        resetUnread(c, userId);
    }

    /** 未读消息总数 */
    public int unreadTotal(Long userId) {
        return conversationMapper.countUnread(userId);
    }

    // ---------------- 发起会话 ----------------

    /**
     * 发起（或复用）会话。同一个「用户对 + 商品」只会有一个会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO start(Long userId, StartConversationReq req) {
        if (req.getPeerId() == null || req.getPeerId() <= 0) {
            throw new BizException("缺少聊天对象");
        }
        if (req.getPeerId().equals(userId)) {
            throw new BizException("不能和自己聊天");
        }
        long goodsId = req.getGoodsId() == null ? 0L : req.getGoodsId();
        long a = Math.min(userId, req.getPeerId());
        long b = Math.max(userId, req.getPeerId());

        Conversation c = conversationMapper.selectByPair(a, b, goodsId);
        if (c == null) {
            c = new Conversation();
            c.setUserA(a);
            c.setUserB(b);
            c.setInitiatorId(userId);
            c.setGoodsId(goodsId);
            c.setGoodsTitle(trim(req.getGoodsTitle(), 120));
            c.setGoodsPrice(parsePrice(req.getGoodsPrice()));
            c.setGoodsEmoji(trim(req.getGoodsEmoji(), 16));
            c.setGoodsStatus(req.getGoodsStatus());
            c.setAnon(req.getAnon() != null && req.getAnon() == 1 ? 1 : 0);
            c.setAnonName(trim(req.getAnonName(), 64));
            c.setUnreadA(0);
            c.setUnreadB(0);
            c.setStatus(1);
            conversationMapper.insert(c);
        }
        return toVO(c, userId, loadBriefs(List.of(req.getPeerId())), true);
    }

    // ---------------- 内部方法 ----------------

    /** 校验会话存在且属于当前用户 */
    public void requireMember(Long conversationId, Long userId) {
        requireConversation(conversationId, userId);
    }

    private Conversation requireConversation(Long conversationId, Long userId) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw BizException.notFound("会话不存在或已删除");
        }
        if (!userId.equals(c.getUserA()) && !userId.equals(c.getUserB())) {
            throw BizException.forbidden("无权访问该会话");
        }
        return c;
    }

    /** 取对端 user id；匿名会话返回 0 */
    private Long peerOf(Conversation c, Long userId) {
        Long peer = userId.equals(c.getUserA()) ? c.getUserB() : c.getUserA();
        return peer == null ? 0L : peer;
    }

    /** 当前用户在该会话的未读数 */
    private int unreadOf(Conversation c, Long userId) {
        Integer n = userId.equals(c.getUserA()) ? c.getUnreadA() : c.getUnreadB();
        return n == null ? 0 : Math.max(0, n);
    }

    private void resetUnread(Conversation c, Long userId) {
        if (userId.equals(c.getUserA())) {
            conversationMapper.resetUnreadA(c.getId());
        } else {
            conversationMapper.resetUnreadB(c.getId());
        }
    }

    private ConversationVO toVO(Conversation c, Long userId, Map<Long, UserBrief> briefs, boolean fillMeta) {
        ConversationVO vo = new ConversationVO();
        vo.setId(c.getId());
        vo.setUnread(unreadOf(c, userId));
        vo.setLastText(c.getLastContent());
        vo.setLastTime(c.getLastTime());

        boolean anon = c.getAnon() != null && c.getAnon() == 1;
        vo.setAnon(anon);

        Long peerId = peerOf(c, userId);
        vo.setPeerId(peerId);
        UserBrief brief = briefs.get(peerId);

        if (anon) {
            vo.setPeerName(StringUtils.hasText(c.getAnonName()) ? c.getAnonName() : "匿名纸条");
            vo.setPeerAvatar("");
            vo.setPeerVerified(false);
            vo.setPeerTags(List.of(new PeerTag("表白墙回声", "pink")));
        } else {
            vo.setPeerName(brief != null && StringUtils.hasText(brief.getNickname())
                    ? brief.getNickname() : "同学");
            vo.setPeerAvatar(brief == null ? "" : nullToEmpty(brief.getAvatar()));
            vo.setPeerVerified(brief != null && Integer.valueOf(2).equals(brief.getVerifyStatus()));
            vo.setPeerTags(buildTags(c, userId, vo.getPeerVerified()));
        }
        vo.setPeerOnline(false);

        if (c.getGoodsId() != null && c.getGoodsId() > 0) {
            vo.setGoodsCard(GoodsCard.of(c.getGoodsId(), c.getGoodsTitle(),
                    c.getGoodsPrice() == null ? null : c.getGoodsPrice().stripTrailingZeros().toPlainString(),
                    c.getGoodsEmoji(), c.getGoodsStatus()));
        }
        if (!fillMeta) {
            vo.setMessages(null);
        }
        return vo;
    }

    /**
     * 会话身份标签：发起人一方视为买家，另一方为卖家。
     */
    private List<PeerTag> buildTags(Conversation c, Long userId, boolean verified) {
        List<PeerTag> tags = new ArrayList<>(2);
        boolean iStarted = userId.equals(c.getInitiatorId());
        if (c.getGoodsId() != null && c.getGoodsId() > 0) {
            // 我发起的会话，对端就是卖家；反之对端是来问我的买家
            tags.add(iStarted ? new PeerTag("卖家", "mint") : new PeerTag("买家", "orange"));
        }
        if (verified) {
            tags.add(new PeerTag("已认证", "plain"));
        }
        return tags;
    }

    /**
     * 批量拉对端资料。
     * 优先走 /internal/users/briefs（一次拉齐）；
     * 若用户服务还是旧版本没有该接口，则退化为逐个查询，保证消息主流程可用。
     */
    private Map<Long, UserBrief> loadBriefs(List<Long> peerIds) {
        if (peerIds == null || peerIds.isEmpty()) {
            return Map.of();
        }
        try {
            R<List<UserBrief>> r = userClient.getBriefs(peerIds);
            if (r != null && r.getData() != null && !r.getData().isEmpty()) {
                Map<Long, UserBrief> map = new HashMap<>();
                for (UserBrief b : r.getData()) {
                    if (b != null && b.getId() != null) {
                        map.put(b.getId(), b);
                    }
                }
                if (!map.isEmpty()) {
                    return map;
                }
            }
        } catch (Exception e) {
            log.warn("批量拉取用户资料失败，退化为逐个查询: {}", e.getMessage());
        }
        return loadBriefsOneByOne(peerIds);
    }

    private Map<Long, UserBrief> loadBriefsOneByOne(List<Long> peerIds) {
        Map<Long, UserBrief> map = new HashMap<>();
        for (Long id : peerIds) {
            if (id == null || id <= 0) {
                continue;
            }
            try {
                R<UserBrief> r = userClient.getBrief(id);
                if (r != null && r.getData() != null) {
                    map.put(id, r.getData());
                }
            } catch (Exception e) {
                log.warn("拉取用户 {} 资料失败: {}", id, e.getMessage());
            }
        }
        return map;
    }

    private int safeCount(java.util.function.IntSupplier supplier) {
        try {
            return supplier.getAsInt();
        } catch (Exception e) {
            log.warn("统计角标失败: {}", e.getMessage());
            return 0;
        }
    }

    private String summarize(String content) {
        if (content == null) {
            return "";
        }
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= SUMMARY_LEN ? oneLine : oneLine.substring(0, SUMMARY_LEN) + "…";
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "text";
        }
        String t = type.trim().toLowerCase();
        return switch (t) {
            case "location", "image", "system" -> t;
            default -> "text";
        };
    }

    private BigDecimal parsePrice(String price) {
        if (!StringUtils.hasText(price)) {
            return null;
        }
        try {
            return new BigDecimal(price.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trim(String v, int max) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        String s = v.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
