package com.campus.message.service;

import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.message.dto.GroupConfigReq;
import com.campus.message.dto.GroupMemberVO;
import com.campus.message.dto.GroupMsgVO;
import com.campus.message.dto.GroupMuteVO;
import com.campus.message.dto.GroupRow;
import com.campus.message.dto.GroupStatsVO;
import com.campus.message.entity.Group;
import com.campus.message.entity.GroupMember;
import com.campus.message.entity.GroupMessage;
import com.campus.message.mapper.GroupMapper;
import com.campus.message.mapper.GroupMemberMapper;
import com.campus.message.mapper.GroupMessageMapper;
import com.campus.message.mapper.GroupMuteMapper;
import com.campus.message.support.GroupTime;
import com.campus.message.ws.WsBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台群管理（campus-admin → 本服务的 /internal/admin/groups/**）。
 *
 * <p>权限点与审计在 campus-admin 侧（{@code @RequirePermission} / {@code @AuditLog}），
 * 这里只做业务与数据一致性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupAdminService {

    /** 一个群最多 5 个管理员：权力分散等于没人负责 */
    private static final int MAX_ADMINS = 5;

    private final GroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final GroupMessageMapper messageMapper;
    private final GroupMuteMapper muteMapper;
    private final GroupService groupService;
    private final GroupJoinService joinService;
    private final GroupMuteService muteService;
    private final WsBroadcaster broadcaster;

    // ---------------- 列表 / 统计 ----------------

    public R.Page<GroupRow> page(String keyword, Integer status, long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(Math.max(1, size), 100);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<GroupRow> rows = groupMapper.pageGroups(kw, status, (p - 1) * s, s);
        long total = groupMapper.countGroups(kw, status);
        return R.Page.of(rows, total, p, s);
    }

    public GroupStatsVO stats() {
        GroupStatsVO vo = new GroupStatsVO();
        vo.setGroupTotal(groupMapper.countAll());
        vo.setMemberTotal(groupMapper.sumMemberCount());
        vo.setTodayMessages(groupMapper.countTodayMessages());
        vo.setActiveGroups(groupMapper.countActiveGroups());
        return vo;
    }

    // ---------------- 群配置 ----------------

    /**
     * 修改群配置。字段为 {@code null} 表示「不改」，逐项判空 ——
     * 直接把 null 当 0 写库会把「自适应」误改成「强制 0 秒」（等于不限速）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(Long groupId, GroupConfigReq req) {
        Group g = groupService.requireGroup(groupId);
        Group patch = new Group();
        patch.setId(groupId);

        if (req.getName() != null) {
            String name = req.getName().trim();
            if (name.isEmpty() || name.length() > 64) {
                throw BizException.of("群名长度需在 1~64 字之间");
            }
            patch.setName(name);
        }
        if (req.getNotice() != null) {
            if (req.getNotice().length() > 500) {
                throw BizException.of("群公告最多 500 字");
            }
            patch.setNotice(req.getNotice().trim());
        }
        if (req.getRateGapSec() != null) {
            if (req.getRateGapSec() < 0 || req.getRateGapSec() > 600) {
                throw BizException.of("发言间隔需在 0~600 秒之间（0 = 按群规模自适应）");
            }
            patch.setRateGapSec(req.getRateGapSec());
        }
        if (req.getRateDayMax() != null) {
            if (req.getRateDayMax() < 0 || req.getRateDayMax() > 500) {
                throw BizException.of("每日上限需在 0~500 条之间（0 = 按群规模自适应）");
            }
            patch.setRateDayMax(req.getRateDayMax());
        }
        // 夜间静音：开关与时段都按群单独配置（本轮核心需求）
        if (req.getNightEnabled() != null) {
            patch.setNightEnabled(req.getNightEnabled() == 1 ? 1 : 0);
        }
        if (req.getNightStart() != null) {
            patch.setNightStart(checkClock(req.getNightStart()));
        }
        if (req.getNightEnd() != null) {
            patch.setNightEnd(checkClock(req.getNightEnd()));
        }
        if (req.getStatus() != null) {
            patch.setStatus(req.getStatus() == 1 ? 1 : 0);
        }
        groupMapper.updateById(patch);
        log.info("[group-admin] 更新群配置 group={} 操作人={} 原因={}", groupId, null, req.getReason());
    }

    /** 后台群详情：只回群本身（「我的状态」是 C 端概念，后台不需要） */
    public java.util.Map<String, Object> detail(Long groupId) {
        com.campus.message.entity.Group g = groupService.requireGroup(groupId);
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", String.valueOf(g.getId()));
        m.put("name", g.getName());
        m.put("notice", g.getNotice());
        m.put("schoolName", g.getSchoolId() == null ? "" : groupMapper.selectSchoolName(g.getSchoolId()));
        m.put("memberCount", g.getMemberCount());
        m.put("lastMsgTime", GroupTime.brief(g.getLastMsgTime()));
        m.put("status", g.getStatus());
        m.put("rateGapSec", g.getRateGapSec());
        m.put("rateDayMax", g.getRateDayMax());
        m.put("nightEnabled", g.getNightEnabled());
        m.put("nightStart", g.getNightStart());
        m.put("nightEnd", g.getNightEnd());
        m.put("createdAt", GroupTime.brief(g.getCreatedAt()));
        return m;
    }

    private String checkClock(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (!v.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            throw BizException.of("时间格式必须是 HH:mm，例如 00:30");
        }
        return v;
    }

    // ---------------- 成员 ----------------

    public R.Page<GroupMemberVO> members(Long groupId, String keyword, long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(Math.max(1, size), 100);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<GroupMemberVO> rows = memberMapper.pageMembers(groupId, kw, (p - 1) * s, s);
        long total = memberMapper.countMembers(groupId, kw);
        for (GroupMemberVO row : rows) {
            row.setMuted(muteService.active(groupId, Long.valueOf(row.getUserId())) != null);
        }
        return R.Page.of(rows, total, p, s);
    }

    /** 设为 / 取消群管理员 */
    @Transactional(rollbackFor = Exception.class)
    public void setRole(Long groupId, Long userId, Integer role) {
        groupService.requireGroup(groupId);
        GroupMember m = memberMapper.selectActive(groupId, userId);
        if (m == null) {
            throw BizException.notFound("该用户不在群里");
        }
        int target = role != null && role == 1 ? 1 : 0;
        if (target == 1 && !Integer.valueOf(1).equals(m.getRole())) {
            if (memberMapper.countAdmins(groupId) >= MAX_ADMINS) {
                throw BizException.of("一个群最多 " + MAX_ADMINS + " 名管理员，请先取消其他管理员");
            }
        }
        memberMapper.updateRole(groupId, userId, target);
        // 权限变更立即告知在线端，避免「后台设了管理员，他还不知道」
        broadcaster.sendToUser(userId, WsBroadcaster.frame("role", "role", target));
        log.info("[group-admin] 设置角色 group={} user={} role={}", groupId, userId, target);
    }

    /** 禁言（hours = 0 表示永久） */
    public void mute(Long groupId, Long userId, Integer hours, String reason, Long operatorId) {
        groupService.requireGroup(groupId);
        if (memberMapper.selectActive(groupId, userId) == null) {
            throw BizException.notFound("该用户不在群里");
        }
        muteService.mute(groupId, userId, hours, reason, operatorId);
        broadcaster.sendToUser(userId, WsBroadcaster.frame("mute", "hours", hours));
    }

    public int unmute(Long groupId, Long userId) {
        int n = muteService.unmute(groupId, userId);
        broadcaster.sendToUser(userId, WsBroadcaster.frame("unmute"));
        return n;
    }

    public void kick(Long groupId, Long userId) {
        groupService.requireGroup(groupId);
        if (memberMapper.selectActive(groupId, userId) == null) {
            throw BizException.notFound("该用户不在群里");
        }
        joinService.kick(groupId, userId);
        // 断开他在这个群的订阅并通知：kick 帧由前端清空聊天页
        broadcaster.sendToUser(userId, WsBroadcaster.frame("kick", "groupId", String.valueOf(groupId)));
        log.info("[group-admin] 踢出成员 group={} user={}", groupId, userId);
    }

    public R.Page<GroupMuteVO> mutes(Long groupId, boolean activeOnly, long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(Math.max(1, size), 100);
        List<GroupMuteVO> rows = muteMapper.pageMutes(groupId, activeOnly, (p - 1) * s, s);
        long total = muteMapper.countMutes(groupId);
        return R.Page.of(rows, total, p, s);
    }

    // ---------------- 消息 ----------------

    public R.Page<GroupMsgVO> messages(Long groupId, String keyword, long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(Math.max(1, size), 100);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<GroupMessage> rows = groupMapper.pageMessagesAdmin(groupId, kw, (p - 1) * s, s);
        long total = groupMapper.countMessagesAdmin(groupId, kw);

        List<GroupMsgVO> list = new ArrayList<>(rows.size());
        for (GroupMessage msg : rows) {
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
            vo.setSelf(false);
            vo.setDeleted(msg.getDeleted());
            vo.setClientMsgId(msg.getClientMsgId());
            vo.setCreatedAt(GroupTime.msg(msg.getCreatedAt()));
            list.add(vo);
        }
        return R.Page.of(list, total, p, s);
    }

    /** 删除违规消息：软删，与用户撤回写同一列（聊天记录是纠纷证据，不做物理删除） */
    public void deleteMessage(Long groupId, Long messageId) {
        if (messageMapper.softDelete(messageId, groupId) == 0) {
            throw BizException.notFound("消息不存在或已删除");
        }
        broadcaster.broadcast(groupId, WsBroadcaster.frame("delete", "messageId", String.valueOf(messageId)));
        log.info("[group-admin] 删除群消息 group={} msg={}", groupId, messageId);
    }
}
