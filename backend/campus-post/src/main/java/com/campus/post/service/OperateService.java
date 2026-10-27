package com.campus.post.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.campus.common.ad.AdSizeCatalog;
import com.campus.common.security.UserContext;
import com.campus.post.dto.AdMaterialVO;
import com.campus.post.dto.NoticeReceipt;
import com.campus.post.dto.NoticeVO;
import com.campus.post.mapper.OperateQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 首页运营位读取：广告素材 + 后台公告。
 *
 * <p>两张表的写入方都是 campus-admin（运营后台），这里只负责把它们<b>安全、准时地</b>
 * 投放到小程序：
 * <ul>
 *   <li><b>安全</b>——严格落实「上架 + 过审 + 投放时间窗 + 定向范围」四道过滤，
 *       其中公告的 target_type 还是一道信息隔离线（后台内部通知不得外泄）；</li>
 *   <li><b>准时</b>——这些都是首页首屏用的，两个查询都很轻，
 *       可以放心放进 Promise.all 与其它接口并发拉取，不会拖慢开屏。</li>
 * </ul>
 *
 * <p>曝光/点击计数刻意做成「尽力而为」：它们是给运营看的效果指标，
 * 统计丢了可以接受，因为一次 UPDATE 失败让首页白屏绝对不行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperateService {

    /** 单次拉取的公告上限，避免公告很多时撑爆首屏 */
    private static final int NOTICE_LIMIT = 20;
    /** 广告位没配 max_count 时的兜底条数 */
    private static final int DEFAULT_MAX_COUNT = 3;

    private final OperateQueryMapper operateMapper;

    /**
     * 拉取某个广告位当前该展示的素材。
     *
     * @param code 广告位编码（后台配置，如 home_banner）
     * @return 最多 max_count 条；广告位不存在 / 未过审 / 不在投放期 → 空列表
     */
    public List<AdMaterialVO> materials(String code, String city) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        Integer max = operateMapper.selectSlotMaxCount(code.trim());
        if (max == null || max <= 0) {
            // slot 不存在或未过审：静默返回空，首页当作「没投广告」处理
            return List.of();
        }
        List<AdMaterialVO> list = operateMapper.selectMaterials(
                code.trim(), city, UserContext.schoolId(), Math.min(max, 20));
        // 展示高度由后台选的规格决定，随素材一起下发：前端拿到才敢撑容器。
        // 不在这里算，小程序就得自己猜高度（历史上各页面各写死一个值，后台改规格永远不生效）。
        for (AdMaterialVO m : list) {
            m.setDisplayHeight(AdSizeCatalog.heightOf(m.getSizeCode()));
        }
        if (!list.isEmpty()) {
            countView(list);
        }
        return list;
    }

    /** 一次曝光（GET）把本次返回的素材各记一次 view_count */
    private void countView(List<AdMaterialVO> list) {
        try {
            for (AdMaterialVO m : list) {
                operateMapper.incrView(m.getId());
            }
        } catch (Exception e) {
            log.warn("[ad] 曝光计数失败，已忽略：{}", e.getMessage());
        }
    }

    /** 点击上报 */
    public void click(Long materialId) {
        if (materialId == null) {
            return;
        }
        try {
            if (operateMapper.countMaterial(materialId) > 0) {
                operateMapper.incrClick(materialId);
            }
        } catch (Exception e) {
            log.warn("[ad] 点击计数失败，已忽略：{}", e.getMessage());
        }
    }

    /** 小程序可见的公告列表（紧急在前，最新在前） */
    public List<NoticeVO> notices(Long schoolId) {
        Long scope = schoolId != null ? schoolId : UserContext.schoolId();
        return operateMapper.selectNotices(scope, NOTICE_LIMIT);
    }

    public NoticeVO notice(Long id, Long schoolId) {
        Long scope = schoolId != null ? schoolId : UserContext.schoolId();
        return operateMapper.selectNotice(id, scope);
    }

    /**
     * 本次进入小程序该弹哪条公告；返回 null 表示不弹。
     *
     * <p>频次语义由后台的 popup_mode 决定，服务端只做「该不该弹」的判定：
     * <ul>
     *   <li>1 每次进入 —— 服务端不拦，靠客户端在本次冷启动内去重（切 tab 不再弹）；</li>
     *   <li>2 每天一次 —— last_shown_at 不是今天才弹；</li>
     *   <li>3 确认收到后不再显示 —— confirmed_at 为空才弹。</li>
     * </ul>
     *
     * <p><b>游客（userId 为空）直接返回候选，不做回执判定</b>：没有 user_id 就落不了
     * t_notice_receipt，硬拦会导致游客永远看不到弹窗。频次改由小程序本地存储兜底，
     * 游客登录后本机记录仍然生效，不会出现「登录完又弹一次」。
     */
    public NoticeVO popup(Long userId) {
        List<NoticeVO> candidates = operateMapper.selectPopupNotices(UserContext.schoolId());
        for (NoticeVO n : candidates) {
            int mode = n.getPopupMode() == null ? 0 : n.getPopupMode();
            if (mode <= 0) {
                continue;
            }
            if (userId == null) {
                return n;
            }
            NoticeReceipt receipt = operateMapper.selectReceipt(userId, n.getId());
            if (shouldPopup(mode, receipt)) {
                markShown(userId, n.getId());
                return n;
            }
        }
        return null;
    }

    /** 频次判定：三种模式各自只看自己那一列，互不干扰 */
    private boolean shouldPopup(int mode, NoticeReceipt receipt) {
        if (mode == 1) {
            return true;
        }
        if (mode == 2) {
            return receipt == null || receipt.getLastShownAt() == null
                    || !LocalDate.now().equals(receipt.getLastShownAt().toLocalDate());
        }
        if (mode == 3) {
            return receipt == null || receipt.getConfirmedAt() == null;
        }
        return false;
    }

    /** 弹出即记一次回执：写失败不影响本次弹出，只是会让「每天一次」多弹一次 */
    private void markShown(Long userId, Long noticeId) {
        try {
            operateMapper.upsertShown(IdWorker.getId(), userId, noticeId);
        } catch (Exception e) {
            log.warn("[notice] 弹窗回执写入失败，已忽略：{}", e.getMessage());
        }
    }

    /**
     * 用户点「我知道了」。游客不落库（没有 user_id），由客户端本地记住。
     *
     * <p>不做「是否已确认」的前置判断：UPDATE 里用 IFNULL 保证了幂等，
     * 重复点不会改写第一次的确认时间。
     */
    public void confirm(Long noticeId, Long userId) {
        if (noticeId == null || userId == null) {
            return;
        }
        if (operateMapper.countNotice(noticeId) == 0) {
            return;
        }
        operateMapper.upsertConfirm(IdWorker.getId(), userId, noticeId);
    }
}
