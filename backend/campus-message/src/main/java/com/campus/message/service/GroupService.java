package com.campus.message.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.api.R;
import com.campus.common.client.SecurityGuard;
import com.campus.common.exception.BizException;
import com.campus.message.dto.GroupCenterVO;
import com.campus.message.dto.GroupDetailVO;
import com.campus.message.dto.GroupMemberVO;
import com.campus.message.dto.GroupMsgVO;
import com.campus.message.dto.GroupVO;
import com.campus.message.dto.SendGroupMsgReq;
import com.campus.message.entity.Group;
import com.campus.message.entity.GroupMember;
import com.campus.message.entity.GroupMessage;
import com.campus.message.entity.GroupMute;
import com.campus.message.mapper.GroupMapper;
import com.campus.message.mapper.GroupMemberMapper;
import com.campus.message.mapper.GroupMessageMapper;
import com.campus.message.support.GroupTime;
import com.campus.message.ws.WsBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 校友群 C 端业务。
 *
 * <p>四个必须遵守的约定：
 * <ol>
 *   <li><b>未读用游标不用 COUNT(*)</b>：{@code id > lastReadId AND sender != me}，最多扫 100 行；</li>
 *   <li><b>@所有人 零写放大</b>：只更新群的一行，绝不批量 +1；</li>
 *   <li><b>禁言判定顺序</b>：单人禁言 → 夜间静音 → 频率 → 内容审核，顺序错会给用户错误的拒绝理由；</li>
 *   <li><b>内容审核在落库之前</b>：{@code purify} 的返回值必须拿去落库，否则 level2 替换不生效。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    /** 单页消息上限：服务端硬截断，不信任前端传的 limit */
    private static final int MAX_PAGE = 50;
    private static final int DEFAULT_PAGE = 30;
    /** 未读数封顶：数到 100 就够了，前端显示 99+ */
    private static final int UNREAD_CAP = 100;
    /** 摘要长度：群卡片只展示一行 */
    private static final int BRIEF_LEN = 40;

    private final GroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final GroupMessageMapper messageMapper;
    private final GroupJoinService joinService;
    private final GroupMuteService muteService;
    private final GroupMentionService mentionService;
    private final GroupRatePolicy ratePolicy;
    private final SecurityGuard securityGuard;
    private final WsBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    // ==========================================================
    // 1. 消息页：我的群卡片
    // ==========================================================

    /**
     * 我的群。{@code schoolId == null} 时返回 {@code needSchool=true}，
     * 前端引导去选学校 —— <b>绝不给一个空群</b>（死群比没有群更伤产品）。
     *
     * <p>已有用户（功能上线前就绑了学校的）在这里被惰性补入群，不用写数据迁移脚本。
     */
    public GroupCenterVO mine(Long userId, Long schoolId) {
        GroupCenterVO vo = new GroupCenterVO();
        vo.setNeedLogin(false);
        if (schoolId == null) {
            vo.setNeedSchool(true);
            return vo;
        }
        Long groupId = joinService.autoJoin(userId, schoolId);
        Group group = requireGroup(groupId);
        GroupMember member = memberMapper.selectOne(groupId, userId);

        vo.setNeedSchool(false);
        vo.setSchoolName(groupMapper.selectSchoolName(schoolId));
        vo.setGroup(buildCard(group, member, userId));
        return vo;
    }

    private GroupVO buildCard(Group g, GroupMember m, Long userId) {
        GroupVO vo = new GroupVO();
        vo.setId(String.valueOf(g.getId()));
        vo.setName(g.getName());
        vo.setAvatar(g.getAvatar());
        vo.setMemberCount(g.getMemberCount());
        vo.setLastMsgText(g.getLastMsgText());
        vo.setLastMsgTime(GroupTime.brief(g.getLastMsgTime()));
        vo.setStatus(g.getStatus());
        vo.setMyRole(m == null ? 0 : m.getRole());

        boolean joined = m != null && Integer.valueOf(1).equals(m.getStatus());
        vo.setJoined(joined);
        if (!joined) {
            // 已退群 / 被踢：不给未读，前端显示「重新加入」
            vo.setUnread(0);
            vo.setMentionUnread(0);
            vo.setAtAll(false);
            vo.setMuted(false);
            vo.setNightMute(false);
            vo.setRulesAgreed(true);
            vo.setNotifyMode(0);
            return vo;
        }

        long lastRead = m.getLastReadId() == null ? 0L : m.getLastReadId();
        int unread = messageMapper.selectUnreadIds(g.getId(), lastRead, userId, UNREAD_CAP).size();
        int atAllUnread = atAllUnread(g, lastRead);
        int mention = (m.getMentionUnread() == null ? 0 : m.getMentionUnread()) + atAllUnread;

        vo.setUnread(unread);
        vo.setMentionUnread(mention);
        vo.setAtAll(atAllUnread > 0);
        vo.setMuted(muteService.active(g.getId(), userId) != null);
        vo.setNightMute(isNightMute(g, m));
        vo.setRulesAgreed(Integer.valueOf(1).equals(m.getRulesAgreed()));
        vo.setNotifyMode(m.getNotifyMode());
        return vo;
    }

    // ==========================================================
    // 2. 群详情
    // ==========================================================

    public GroupDetailVO detail(Long groupId, Long userId, Long schoolId) {
        Group g = requireGroup(groupId);
        GroupMember m = memberMapper.selectActive(groupId, userId);
        if (m == null) {
            GroupMember any = memberMapper.selectOne(groupId, userId);
            if (any != null) {
                if (Integer.valueOf(0).equals(any.getStatus())) {
                    throw BizException.forbidden("你已被移出本群");
                }
                throw BizException.forbidden("你已退出本群，可重新加入");
            }
            // 没有任何记录：学校匹配就自愈补加入（Feign 回调丢失时的兜底）
            if (schoolId != null && Objects.equals(schoolId, g.getSchoolId())) {
                joinService.autoJoin(userId, schoolId);
                m = memberMapper.selectActive(groupId, userId);
            }
            if (m == null) {
                throw BizException.forbidden("你不在这个群里");
            }
        }

        GroupDetailVO vo = new GroupDetailVO();
        vo.setId(String.valueOf(g.getId()));
        vo.setName(g.getName());
        vo.setAvatar(g.getAvatar());
        vo.setNotice(g.getNotice());
        vo.setMemberCount(g.getMemberCount());
        vo.setStatus(g.getStatus());
        vo.setMyRole(m.getRole());
        vo.setRulesAgreed(Integer.valueOf(1).equals(m.getRulesAgreed()));
        vo.setNotifyMode(m.getNotifyMode());
        long lastRead = m.getLastReadId() == null ? 0L : m.getLastReadId();
        vo.setLastReadId(String.valueOf(lastRead));

        int unread = messageMapper.selectUnreadIds(g.getId(), lastRead, userId, UNREAD_CAP).size();
        int atAllUnread = atAllUnread(g, lastRead);
        vo.setUnread(unread);
        vo.setMentionUnread((m.getMentionUnread() == null ? 0 : m.getMentionUnread()) + atAllUnread);
        vo.setAtAll(atAllUnread > 0);
        if (m.getMentionUnread() != null && m.getMentionUnread() > 0) {
            vo.setUnreadMentionIds(mentionService.unreadMessageIds(groupId, userId, 20).stream()
                    .map(String::valueOf).toList());
        } else {
            vo.setUnreadMentionIds(List.of());
        }

        GroupRatePolicy.RateLimit limit = GroupRatePolicy.resolve(g);
        vo.setRateGapSec(limit.gapSec());
        vo.setRateDayMax(limit.dayMax());
        vo.setNightRange(g.getNightStart() + "-" + g.getNightEnd());
        vo.setAtAllAllowed(m.getRole() != null && m.getRole() >= 1);

        // 不能发言的原因按「禁言 → 夜间 → 停用」优先级取第一个，只给一个理由
        GroupMute mute = muteService.active(groupId, userId);
        vo.setMuted(mute != null);
        if (mute != null) {
            vo.setMuteEndAt(GroupMuteService.describe(mute));
            vo.setMuteReason(mute.getReason());
        }
        boolean night = isNightMute(g, m);
        vo.setNightMute(night);

        if (Integer.valueOf(0).equals(g.getStatus())) {
            vo.setCanSend(false);
            vo.setBlockReason("本群已停用，暂时无法发言");
        } else if (mute != null) {
            vo.setCanSend(false);
            vo.setBlockReason("你已被禁言至 " + GroupMuteService.describe(mute)
                    + (mute.getReason() == null || mute.getReason().isBlank() ? "" : "，原因：" + mute.getReason()));
        } else if (night) {
            vo.setCanSend(false);
            vo.setBlockReason("本群 " + g.getNightStart() + "-" + g.getNightEnd()
                    + " 夜间静默，可浏览不可发言");
        } else {
            vo.setCanSend(true);
            vo.setBlockReason("");
        }
        return vo;
    }

    // ==========================================================
    // 3. 消息拉取（首次 / 补拉 / 翻历史，同一套游标语义）
    // ==========================================================

    /**
     * @param sinceId  增量补拉：只返回更新的（正序），WebSocket 重连后必调
     * @param beforeId 上滑翻历史：返回更早的一批（正序）
     * @param limit    默认 30，服务端硬上限 50
     */
    public List<GroupMsgVO> messages(Long groupId, Long userId, Long sinceId, Long beforeId, Integer limit) {
        requireActiveMember(groupId, userId);
        int size = limit == null || limit <= 0 ? DEFAULT_PAGE : Math.min(limit, MAX_PAGE);

        List<GroupMessage> rows;
        if (sinceId != null && sinceId > 0) {
            rows = messageMapper.selectSince(groupId, sinceId, size);
        } else if (beforeId != null && beforeId > 0) {
            rows = messageMapper.selectBefore(groupId, beforeId, size);
            Collections.reverse(rows);
        } else {
            rows = messageMapper.selectRecent(groupId, size);
            Collections.reverse(rows);
        }

        List<GroupMsgVO> list = new ArrayList<>(rows.size());
        for (GroupMessage msg : rows) {
            list.add(toVO(msg, userId));
        }
        refreshAuthors(list);
        return list;
    }

    // ==========================================================
    // 4. 发消息（HTTP 与 WebSocket 共用这一条）
    // ==========================================================

    @Transactional(rollbackFor = Exception.class)
    public GroupMsgVO send(Long groupId, Long userId, SendGroupMsgReq req) {
        Group g = requireGroup(groupId);
        GroupMember me = memberMapper.selectActive(groupId, userId);
        if (me == null) {
            throw BizException.forbidden("你不在这个群里");
        }
        boolean admin = me.getRole() != null && me.getRole() >= 1;

        // ① 单人禁言 —— 必须先判，否则被禁言的人会收到「发言太频繁」这种误导性提示
        GroupMute mute = muteService.active(groupId, userId);
        if (mute != null) {
            throw BizException.forbidden("你已被禁言至 " + GroupMuteService.describe(mute)
                    + (mute.getReason() == null || mute.getReason().isBlank() ? "" : "，原因：" + mute.getReason()));
        }
        // ② 夜间静音（管理员豁免：夜里出违规内容得有人能说话处置）
        if (isNightMute(g, me)) {
            throw BizException.forbidden("本群 " + g.getNightStart() + "-" + g.getNightEnd()
                    + " 夜间静默，可浏览不可发言");
        }
        // ③ 频率
        ratePolicy.check(g, userId, admin);

        if (Integer.valueOf(0).equals(g.getStatus())) {
            throw BizException.forbidden("本群已停用，暂时无法发言");
        }

        // ④ 内容准备与审核
        String type = req.getType() == null || req.getType().isBlank() ? "text" : req.getType().trim();
        if (!"text".equals(type) && !"image".equals(type)) {
            throw BizException.of("不支持的消息类型");
        }
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (content.isEmpty()) {
            throw BizException.of("说点什么吧");
        }
        if (content.length() > 1000) {
            throw BizException.of("内容太长了（最多 1000 字）");
        }
        if ("image".equals(type) && !content.startsWith("http")) {
            throw BizException.of("图片地址无效，请重新上传");
        }
        if ("text".equals(type)) {
            // purify 的返回值必须拿去落库，否则 level2（替换）等于没生效
            content = securityGuard.purify(userId, content, SecurityGuard.BIZ_GROUP_MSG);
            securityGuard.checkText(userId, content, SecurityGuard.SCENE_FORUM, SecurityGuard.BIZ_GROUP_MSG);
        }

        // @ 目标：最多 10 人；@所有人 仅管理员且每天 3 次
        boolean atAll = Boolean.TRUE.equals(req.getAtAll());
        List<Long> atIds = parseAtIds(req.getAtUserIds());
        if (atAll) {
            if (!admin) {
                throw BizException.forbidden("只有群管理员可以 @所有人");
            }
            ratePolicy.checkAtAll(groupId, userId);
            atIds = List.of();
        }

        // ⑤ 落库（clientMsgId 幂等）
        String clientMsgId = req.getClientMsgId();
        if (clientMsgId == null || clientMsgId.isBlank()) {
            // 必须给个值：uk_client 上的空字符串会让第二条无 id 的消息直接冲突
            clientMsgId = UUID.randomUUID().toString().replace("-", "");
        }

        GroupMessage msg = new GroupMessage();
        msg.setGroupId(groupId);
        msg.setSenderId(userId);
        msg.setSenderName("");
        msg.setSenderAvatar("");
        msg.setType(type);
        msg.setContent(content);
        msg.setAtType(atAll ? 2 : (atIds.isEmpty() ? 0 : 1));
        msg.setAtUserIds(toJson(atIds));
        msg.setDeleted(0);
        msg.setClientMsgId(clientMsgId);

        try {
            messageMapper.insert(msg);
        } catch (DuplicateKeyException e) {
            // 断线重发 / 连点：返回已存在的那条，不再插第二条
            GroupMessage exists = messageMapper.selectOne(Wrappers.<GroupMessage>lambdaQuery()
                    .eq(GroupMessage::getGroupId, groupId)
                    .eq(GroupMessage::getSenderId, userId)
                    .eq(GroupMessage::getClientMsgId, clientMsgId)
                    .last("limit 1"));
            if (exists != null) {
                return toVO(exists, userId);
            }
            throw BizException.of("发送失败，请重试");
        }

        // ⑥ 群摘要 + @ 效果
        groupMapper.updateLastMessage(groupId, msg.getId(), brief(content, type), LocalDateTime.now());
        mentionService.apply(groupId, msg.getId(), userId, atIds, atAll);

        // ⑦ 广播（自己发的也走这里，前端按 clientMsgId 替换本地气泡）
        GroupMsgVO vo = toVO(msg, null);   // self 由前端用 senderId 判断
        broadcaster.broadcast(groupId, WsBroadcaster.frame("msg", "message", vo));
        return toVO(msg, userId);
    }

    // ==========================================================
    // 5. 已读 / 群规 / 提醒 / 成员 / 进退群
    // ==========================================================

    @Transactional(rollbackFor = Exception.class)
    public void read(Long groupId, Long userId, Long lastReadId) {
        requireActiveMember(groupId, userId);
        memberMapper.advanceRead(groupId, userId, lastReadId == null ? 0L : lastReadId);
        mentionService.readAll(groupId, userId);
    }

    public void agreeRules(Long groupId, Long userId) {
        GroupMember m = requireActiveMember(groupId, userId);
        if (Integer.valueOf(1).equals(m.getRulesAgreed())) {
            return;
        }
        GroupMember patch = new GroupMember();
        patch.setId(m.getId());
        patch.setRulesAgreed(1);
        memberMapper.updateById(patch);
    }

    public void setNotifyMode(Long groupId, Long userId, Integer mode) {
        if (mode == null || mode < 0 || mode > 2) {
            throw BizException.of("提醒模式不合法");
        }
        GroupMember m = requireActiveMember(groupId, userId);
        GroupMember patch = new GroupMember();
        patch.setId(m.getId());
        patch.setNotifyMode(mode);
        memberMapper.updateById(patch);
    }

    public R.Page<GroupMemberVO> members(Long groupId, Long userId, String keyword, long page, long size) {
        requireActiveMember(groupId, userId);
        long p = Math.max(1, page);
        long s = Math.min(Math.max(1, size), 50);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<GroupMemberVO> rows = memberMapper.pageMembers(groupId, kw, (p - 1) * s, s);
        long total = memberMapper.countMembers(groupId, kw);
        for (GroupMemberVO row : rows) {
            row.setMuted(muteService.active(groupId, Long.valueOf(row.getUserId())) != null);
        }
        return R.Page.of(rows, total, p, s);
    }

    public void rejoin(Long groupId, Long userId) {
        requireGroup(groupId);
        joinService.rejoin(groupId, userId);
    }

    public void quit(Long groupId, Long userId) {
        requireGroup(groupId);
        joinService.quit(groupId, userId);
    }

    /** 绑定学校后自动入群（campus-user 通过 Feign 调用） */
    public Long autoJoin(Long userId, Long schoolId) {
        return joinService.autoJoin(userId, schoolId);
    }

    /** 群最新消息 id：前端「全部已读」不传游标时用它兜底 */
    public Long latestMessageId(Long groupId) {
        Group g = requireGroup(groupId);
        return g.getLastMsgId() == null ? 0L : g.getLastMsgId();
    }

    public boolean isActiveMember(Long groupId, Long userId) {
        return memberMapper.selectActive(groupId, userId) != null;
    }

    // ==========================================================
    // 内部
    // ==========================================================

    public Group requireGroup(Long groupId) {
        Group g = groupId == null ? null : groupMapper.selectById(groupId);
        if (g == null) {
            throw BizException.notFound("群不存在");
        }
        return g;
    }

    private GroupMember requireActiveMember(Long groupId, Long userId) {
        GroupMember m = memberMapper.selectActive(groupId, userId);
        if (m == null) {
            throw BizException.forbidden("你不在这个群里");
        }
        return m;
    }

    /**
     * 是否夜间静默。<b>管理员豁免</b>，跨天逻辑复用消息模块既有的
     * {@code MessageSettingService#inDnd}（支持 23:00-07:00 这类跨零点时段）。
     */
    private boolean isNightMute(Group g, GroupMember m) {
        if (!Integer.valueOf(1).equals(g.getNightEnabled())) {
            return false;
        }
        if (m != null && m.getRole() != null && m.getRole() >= 1) {
            return false;   // 管理员豁免
        }
        return MessageSettingService.inDnd(g.getNightStart(), g.getNightEnd(), LocalTime.now());
    }

    private int atAllUnread(Group g, long lastRead) {
        Long lastAtAll = g.getLastAtAllMsgId();
        if (lastAtAll == null || lastAtAll <= lastRead) {
            return 0;   // 快路径：群上的游标就说明没有未读的 @全体，不必查消息表
        }
        return messageMapper.countAtAllUnread(g.getId(), lastRead);
    }

    private GroupMsgVO toVO(GroupMessage msg, Long viewerId) {
        GroupMsgVO vo = new GroupMsgVO();
        vo.setId(String.valueOf(msg.getId()));
        vo.setGroupId(String.valueOf(msg.getGroupId()));
        vo.setSenderId(String.valueOf(msg.getSenderId()));
        vo.setSenderName(msg.getSenderName());
        vo.setSenderAvatar(msg.getSenderAvatar());
        vo.setType(msg.getType());
        vo.setContent(msg.getContent());
        vo.setAtType(msg.getAtType());
        vo.setAtAll(Integer.valueOf(2).equals(msg.getAtType()));
        vo.setAtUserIds(parseAtJson(msg.getAtUserIds()));
        vo.setSelf(viewerId != null && Objects.equals(viewerId, msg.getSenderId()));
        vo.setDeleted(msg.getDeleted());
        vo.setClientMsgId(msg.getClientMsgId());
        vo.setCreatedAt(GroupTime.msg(msg.getCreatedAt()));
        return vo;
    }

    /**
     * 用最新资料覆盖昵称头像。
     *
     * <p>消息表里的 senderName 是发送时的快照；用户改了昵称/头像后，
     * 历史消息不该永远顶着旧名字（与项目里 Post/Goods 的 refreshAuthors 同一套做法）。
     * 一页最多 50 条，一次 IN 查询解决，不做 N+1。
     */
    private void refreshAuthors(List<GroupMsgVO> list) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream()
                .map(v -> Long.valueOf(v.getSenderId()))
                .distinct().toList();
        List<Map<String, Object>> rows = groupMapper.selectUserBriefs(ids);
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byId.put(String.valueOf(row.get("id")), row);
        }
        for (GroupMsgVO vo : list) {
            Map<String, Object> u = byId.get(vo.getSenderId());
            if (u == null) {
                continue;
            }
            Object nickname = u.get("nickname");
            Object avatar = u.get("avatar");
            if (nickname != null && !String.valueOf(nickname).isBlank()) {
                vo.setSenderName(String.valueOf(nickname));
            }
            if (avatar != null) {
                vo.setSenderAvatar(String.valueOf(avatar));
            }
        }
    }

    private String brief(String content, String type) {
        if ("image".equals(type)) {
            return "[图片]";
        }
        String s = content.replaceAll("\\s+", " ").trim();
        return s.length() <= BRIEF_LEN ? s : s.substring(0, BRIEF_LEN) + "…";
    }

    private List<Long> parseAtIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(s.trim()));
            } catch (NumberFormatException ignored) {
                // 非法 id 直接跳过，不因为一个坏参数把整条消息拒掉
            }
            if (ids.size() >= GroupMentionService.MAX_AT_PER_MESSAGE) {
                break;
            }
        }
        return ids;
    }

    private String toJson(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "";
        }
    }

    private List<String> parseAtJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(json, List.class);
            List<String> out = new ArrayList<>(raw.size());
            for (Object o : raw) {
                out.add(String.valueOf(o));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
