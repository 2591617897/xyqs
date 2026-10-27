package com.campus.message.service;

import com.campus.message.dto.MessageSettingsVO;
import com.campus.message.entity.MessageSetting;
import com.campus.message.mapper.MessageSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 消息提醒设置。
 *
 * <p>三件事：站内开关、免打扰时段、微信订阅消息授权状态。
 * 读多写少 —— 没记录时返回默认值而不插空行，只有真正改过设置才落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSettingService {

    /** HH:mm（24 小时制，允许 0:00 这种写法） */
    private static final Pattern HH_MM = Pattern.compile("^([01]?\\d|2[0-3]):[0-5]\\d$");

    private final MessageSettingMapper settingMapper;

    /**
     * 订阅消息模板 id，逗号分隔。
     *
     * <p>为什么放配置而不是前端常量：模板 id 只有在小程序后台「订阅消息 → 我的模板」里
     * 才有，后期绑定服务号 / 换模板时改环境变量即可，前端不用发版。
     * 没配就是空列表，前端据此「只展示不请求授权」。
     */
    @Value("${campus.wx.subscribe-templates:}")
    private String subscribeTemplates;

    /** 读取设置：没有记录就返回默认值（全开、免打扰关、未授权） */
    public MessageSettingsVO get(Long userId) {
        MessageSettingsVO vo = new MessageSettingsVO();
        MessageSetting row = settingMapper.selectById(userId);
        if (row != null) {
            vo.setNotifyMessage(flag(row.getNotifyMessage()));
            vo.setNotifyLike(flag(row.getNotifyLike()));
            vo.setNotifyFollow(flag(row.getNotifyFollow()));
            vo.setNotifyComment(flag(row.getNotifyComment()));
            vo.setNotifySystem(flag(row.getNotifySystem()));
            vo.setDndEnabled(flag(row.getDndEnabled()));
            vo.setDndStart(validTime(row.getDndStart(), "22:00"));
            vo.setDndEnd(validTime(row.getDndEnd(), "07:00"));
            vo.setSubscribeStatus(row.getSubscribeStatus() == null ? 0 : row.getSubscribeStatus());
        }
        applyTemplateConfig(vo);
        return vo;
    }

    /** 保存开关与免打扰（不动订阅状态，那个只能由微信授权回调改） */
    @Transactional(rollbackFor = Exception.class)
    public MessageSettingsVO save(Long userId, MessageSettingsVO req) {
        MessageSetting row = settingMapper.selectById(userId);
        boolean isNew = row == null;
        if (isNew) {
            row = new MessageSetting();
            row.setUserId(userId);
        }
        if (req.getNotifyMessage() != null) row.setNotifyMessage(bit(req.getNotifyMessage()));
        if (req.getNotifyLike() != null) row.setNotifyLike(bit(req.getNotifyLike()));
        if (req.getNotifyFollow() != null) row.setNotifyFollow(bit(req.getNotifyFollow()));
        if (req.getNotifyComment() != null) row.setNotifyComment(bit(req.getNotifyComment()));
        if (req.getNotifySystem() != null) row.setNotifySystem(bit(req.getNotifySystem()));
        if (req.getDndEnabled() != null) row.setDndEnabled(bit(req.getDndEnabled()));
        // 时间串一律白名单校验：这东西会直接拼进前端的展示与后端的判定，不能被塞进奇怪字符
        if (req.getDndStart() != null) row.setDndStart(validTime(req.getDndStart(), "22:00"));
        if (req.getDndEnd() != null) row.setDndEnd(validTime(req.getDndEnd(), "07:00"));

        if (isNew) {
            // 新行的必填列先给默认值，避免 NOT NULL 冲突
            if (row.getNotifyMessage() == null) row.setNotifyMessage(1);
            if (row.getNotifyLike() == null) row.setNotifyLike(1);
            if (row.getNotifyFollow() == null) row.setNotifyFollow(1);
            if (row.getNotifyComment() == null) row.setNotifyComment(1);
            if (row.getNotifySystem() == null) row.setNotifySystem(1);
            if (row.getDndEnabled() == null) row.setDndEnabled(0);
            if (row.getDndStart() == null) row.setDndStart("22:00");
            if (row.getDndEnd() == null) row.setDndEnd("07:00");
            row.setSubscribeStatus(0);
            settingMapper.insert(row);
        } else {
            settingMapper.updateById(row);
        }
        return get(userId);
    }

    /**
     * 上报微信订阅消息的授权结果。
     *
     * @param status 1 已授权 / 2 已拒绝 / 0 重置
     */
    @Transactional(rollbackFor = Exception.class)
    public MessageSettingsVO markSubscribe(Long userId, int status) {
        int s = (status == 1 || status == 2) ? status : 0;
        if (settingMapper.updateSubscribe(userId, s) == 0) {
            // 还没有设置行：先建一行默认值，再标记订阅状态
            MessageSetting row = new MessageSetting();
            row.setUserId(userId);
            row.setNotifyMessage(1);
            row.setNotifyLike(1);
            row.setNotifyFollow(1);
            row.setNotifyComment(1);
            row.setNotifySystem(1);
            row.setDndEnabled(0);
            row.setDndStart("22:00");
            row.setDndEnd("07:00");
            row.setSubscribeStatus(s);
            settingMapper.insert(row);
        }
        return get(userId);
    }

    /**
     * 五类提醒的「有效开关」，供未读计数与后续推送共用。
     *
     * <p>语义：关掉的类目<b>不再计入角标</b>（也不推送），但消息本身完整保留在列表里。
     * 这样用户点开关能立刻看到红点变化，而不是点了没反应。
     *
     * @return 顺序固定为 私信 / 赞与收藏 / 新增粉丝 / 评论与@ / 官方通知
     */
    public EffectiveFlags effectiveFlags(Long userId) {
        MessageSetting row = userId == null ? null : settingMapper.selectById(userId);
        if (row == null) return new EffectiveFlags(true, true, true, true, true);
        return new EffectiveFlags(
                row.getNotifyMessage() == null || row.getNotifyMessage() == 1,
                row.getNotifyLike() == null || row.getNotifyLike() == 1,
                row.getNotifyFollow() == null || row.getNotifyFollow() == 1,
                row.getNotifyComment() == null || row.getNotifyComment() == 1,
                row.getNotifySystem() == null || row.getNotifySystem() == 1
        );
    }

    /** 五类提醒的有效开关快照 */
    public static class EffectiveFlags {
        public final boolean notifyMessage;
        public final boolean notifyLike;
        public final boolean notifyFollow;
        public final boolean notifyComment;
        public final boolean notifySystem;

        public EffectiveFlags(boolean notifyMessage, boolean notifyLike, boolean notifyFollow,
                              boolean notifyComment, boolean notifySystem) {
            this.notifyMessage = notifyMessage;
            this.notifyLike = notifyLike;
            this.notifyFollow = notifyFollow;
            this.notifyComment = notifyComment;
            this.notifySystem = notifySystem;
        }
    }

    /** 当前时间点是否落在免打扰区间（跨天区间如 22:00 → 07:00 也算对） */
    public static boolean inDnd(String start, String end, java.time.LocalTime now) {
        java.time.LocalTime s = parse(start);
        java.time.LocalTime e = parse(end);
        if (s == null || e == null || s.equals(e)) return false;
        if (s.isBefore(e)) {
            return !now.isBefore(s) && now.isBefore(e);
        }
        // 跨天：22:00-24:00 或 00:00-07:00 两段都算
        return !now.isBefore(s) || now.isBefore(e);
    }

    // ---------------- 内部 ----------------

    private void applyTemplateConfig(MessageSettingsVO vo) {
        List<String> ids = parseTemplateIds(subscribeTemplates);
        vo.setTemplateIds(ids);
        vo.setWxPushAvailable(!ids.isEmpty());
        vo.setWxPushOn(!ids.isEmpty() && vo.getSubscribeStatus() != null && vo.getSubscribeStatus() == 1);
    }

    private static List<String> parseTemplateIds(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private static Boolean flag(Integer v) {
        return v != null && v == 1;
    }

    private static Integer bit(Boolean b) {
        return Boolean.TRUE.equals(b) ? 1 : 0;
    }

    private static String validTime(String v, String fallback) {
        if (v == null) return fallback;
        String t = v.trim();
        return HH_MM.matcher(t).matches() ? t : fallback;
    }

    private static java.time.LocalTime parse(String hhmm) {
        try {
            String[] parts = hhmm.split(":");
            return java.time.LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            return null;
        }
    }
}
