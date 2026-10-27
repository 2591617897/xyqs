package com.campus.message.service;

import com.campus.common.exception.BizException;
import com.campus.message.entity.Group;
import com.campus.message.entity.GroupMember;
import com.campus.message.mapper.GroupMapper;
import com.campus.message.mapper.GroupMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 入群 / 建群 / 退群。
 *
 * <p>单独成 Bean 而不是塞进 {@code GroupService}：{@code @Transactional} 靠代理生效，
 * 同类内部自调用（{@code this.autoJoin()}）会绕过代理，事务静默失效 —— 这是最容易
 * 埋下去、又最难查的一类问题。拆出来就没有这个隐患。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupJoinService {

    /** 1 = 校友群。2（同城群）预留，本轮不产出数据 */
    public static final int TYPE_SCHOOL = 1;

    private static final String DEFAULT_NOTICE =
            "欢迎来到本校校友群！可以找同学、问课程、拼车回家、互通消息。\n"
            + "请友善发言，禁止广告与人身攻击；违规内容会被删除并可能被禁言。\n"
            + "夜间 00:30-06:30 为静默时段（管理员可调），可浏览不可发言。";

    private final GroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;

    /**
     * 取学校群，没有就建。
     *
     * <p>并发建群：一万人同时绑定学校时，多个线程会同时判定「群不存在」。
     * 靠 {@code uk_type_key} 唯一键兜底，冲突者重查即可。
     */
    public Group ensureGroup(Long schoolId) {
        String bizKey = String.valueOf(schoolId);
        Group group = groupMapper.selectByTypeAndKey(TYPE_SCHOOL, bizKey);
        if (group != null) {
            return group;
        }
        String schoolName = groupMapper.selectSchoolName(schoolId);
        Group g = new Group();
        g.setType(TYPE_SCHOOL);
        g.setBizKey(bizKey);
        g.setSchoolId(schoolId);
        g.setName((schoolName == null || schoolName.isBlank() ? "本校" : schoolName.trim()) + "校友群");
        g.setAvatar("");
        g.setNotice(DEFAULT_NOTICE);
        g.setMemberCount(0);
        g.setLastMsgId(0L);
        g.setLastMsgText("");
        g.setLastAtAllMsgId(0L);
        g.setRateGapSec(0);
        g.setRateDayMax(0);
        g.setNightEnabled(1);
        g.setNightStart("00:30");
        g.setNightEnd("06:30");
        g.setStatus(1);
        try {
            groupMapper.insert(g);
            log.info("[group] 自动建群 school={} name={}", schoolId, g.getName());
            return g;
        } catch (DuplicateKeyException e) {
            // ⚠️ 必须直接返回重查结果：继续往下走会重复插成员、重复加成员数
            return groupMapper.selectByTypeAndKey(TYPE_SCHOOL, bizKey);
        }
    }

    /**
     * 绑定学校后自动入群。返回群 id。
     *
     * <p>关键语义：<b>只有「完全没有成员记录」才自动加入</b>。
     * {@code status=0}（被踢）、{@code status=2}（主动退群）都尊重现状不拉回，
     * 否则用户退了又被系统加回去，是最伤体验的一类 bug。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long autoJoin(Long userId, Long schoolId) {
        Group group = ensureGroup(schoolId);
        Long groupId = group.getId();

        GroupMember exists = memberMapper.selectOne(groupId, userId);
        if (exists != null) {
            return groupId;
        }
        try {
            memberMapper.insert(newMember(groupId, userId));
        } catch (DuplicateKeyException e) {
            // 并发（同一用户两次绑定回调 / 双端同时进群）：直接返回，别再加成员数
            return groupId;
        }
        groupMapper.incrMemberCount(groupId, 1);
        return groupId;
    }

    /** 主动退群后重新加入 */
    @Transactional(rollbackFor = Exception.class)
    public void rejoin(Long groupId, Long userId) {
        GroupMember m = memberMapper.selectOne(groupId, userId);
        if (m == null) {
            // 没有记录 = 从没入过群，走正常加入
            try {
                memberMapper.insert(newMember(groupId, userId));
            } catch (DuplicateKeyException e) {
                return;
            }
            groupMapper.incrMemberCount(groupId, 1);
            return;
        }
        if (Integer.valueOf(1).equals(m.getStatus())) {
            return;   // 已经在群里，幂等
        }
        if (Integer.valueOf(0).equals(m.getStatus())) {
            throw BizException.forbidden("你已被移出本群，无法自行重新加入");
        }
        if (memberMapper.restore(groupId, userId) > 0) {
            groupMapper.incrMemberCount(groupId, 1);
        }
    }

    /** 主动退群：只改状态保留记录，之后再也不会被自动入群拉回来 */
    @Transactional(rollbackFor = Exception.class)
    public void quit(Long groupId, Long userId) {
        if (memberMapper.leave(groupId, userId, 2) > 0) {
            groupMapper.incrMemberCount(groupId, -1);
        }
    }

    /** 被管理员踢出：status=0，不能自行重新加入 */
    @Transactional(rollbackFor = Exception.class)
    public void kick(Long groupId, Long userId) {
        if (memberMapper.leave(groupId, userId, 0) > 0) {
            groupMapper.incrMemberCount(groupId, -1);
        }
    }

    private GroupMember newMember(Long groupId, Long userId) {
        GroupMember m = new GroupMember();
        m.setGroupId(groupId);
        m.setUserId(userId);
        m.setRole(0);
        m.setLastReadId(0L);
        m.setMentionUnread(0);
        // 默认「仅 @ 提醒」：万人群如果每条消息都提醒，用户第一天就会关掉通知甚至卸载
        m.setNotifyMode(0);
        m.setRulesAgreed(0);
        m.setStatus(1);
        m.setJoinAt(java.time.LocalDateTime.now());
        return m;
    }
}
