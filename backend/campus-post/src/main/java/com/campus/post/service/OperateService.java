package com.campus.post.service;

import com.campus.common.security.UserContext;
import com.campus.post.dto.AdMaterialVO;
import com.campus.post.dto.NoticeVO;
import com.campus.post.mapper.OperateQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
}
