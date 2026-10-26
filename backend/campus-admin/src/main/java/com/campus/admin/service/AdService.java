package com.campus.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.config.AdSlotCatalog;
import com.campus.admin.config.DataScope;
import com.campus.admin.config.DataScopeResolver;
import com.campus.admin.dto.AdSlotOption;
import com.campus.admin.dto.AdTargetOptions;
import com.campus.admin.entity.AdMaterial;
import com.campus.admin.entity.AdSlot;
import com.campus.admin.mapper.AdMaterialMapper;
import com.campus.admin.mapper.AdSlotMapper;
import com.campus.admin.mapper.RegionDictMapper;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 广告位与素材管理。
 *
 * <p>「审核」与「上下架」是两道独立的闸：
 * audit_status（0待审 1通过 2驳回）决定能不能上架，status（0下架 1上架）决定现在投不投。
 * 未过审的素材即使点了上架也会被拒绝——运营误操作不会直接把未审素材推到全校面前。
 *
 * <p>投放范围 scope_type：1全国 2城市 3校区，与 t_admin_region 的口径对齐，
 * 校区负责人只能维护自己辖区内的广告位。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdService {

    private final AdSlotMapper slotMapper;
    private final AdMaterialMapper materialMapper;
    private final DataScopeResolver dataScopeResolver;
    /** 城市 / 校区下拉的字典来源（直读 t_school，与「组织节点」共用一份） */
    private final RegionDictMapper dictMapper;

    // ---------------- 广告位 ----------------

    public R.Page<AdSlot> pageSlots(String keyword, Integer position, Integer scopeType, Integer status,
                                    long page, long size) {
        DataScope ds = dataScopeResolver.resolve();
        List<Long> scopeIds = ds.isAll() ? null : ds.getSchoolIds();
        LambdaQueryWrapper<AdSlot> qw = Wrappers.<AdSlot>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), AdSlot::getName, keyword)
                .eq(position != null, AdSlot::getPosition, position)
                .eq(scopeType != null, AdSlot::getScopeType, scopeType)
                .eq(status != null, AdSlot::getStatus, status);
        // 必须写在外层 if 里：apply(condition, sql) 的第二个参数会被**提前求值**，
        // 写成 apply(scopeIds != null, scopeCondition(scopeIds)) 在超管（scopeIds=null）时
        // 直接空指针——条件虽然为 false，SQL 片段照样要先算出来。
        if (scopeIds != null) {
            // 辖区过滤：校区级广告位只要命中任意一个管辖校区就可见，
            // 全国/城市级广告位对辖区管理员同样可见（它们本来就不属于任何校区）。
            qw.apply(scopeCondition(scopeIds));
        }
        qw.orderByDesc(AdSlot::getSort).orderByDesc(AdSlot::getId);
        IPage<AdSlot> p = slotMapper.selectPage(new Page<>(page, size), qw);
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    /**
     * 辖区过滤条件。
     *
     * <p>school_ids 改成逗号分隔多值后不能再写 {@code IN}：一个广告位可能同时投给
     * 辖区内的 3 所学校，用 IN 只能命中「恰好等于某一个」的行。
     *
     * <p>同时修掉一个旧缺陷：原来是 {@code school_id IN (...)}，全国/城市级广告位
     * 的 school_id 是 NULL，{@code NULL IN (...)} 结果为 NULL 而不是 FALSE，
     * 行会被悄悄过滤掉——辖区管理员根本看不到总部配的全国广告位。
     *
     * <p>拼进去的是 {@link DataScope#getSchoolIds()} 里的 Long，不来自请求参数。
     */
    private String scopeCondition(List<Long> scopeIds) {
        if (scopeIds.isEmpty()) {
            return "scope_type <> 3";
        }
        String hit = scopeIds.stream()
                .map(id -> "FIND_IN_SET(" + id + ", school_ids)")
                .collect(java.util.stream.Collectors.joining(" OR "));
        return "(scope_type <> 3 OR " + hit + ")";
    }

    /** 后台下拉用的可选编码；前端拿它渲染「广告位」选择器，不再手填编码 */
    public List<AdSlotOption> slotOptions() {
        return AdSlotCatalog.all();
    }

    /**
     * 「投放范围」多选的候选值：城市 + 校区。
     *
     * <p>与编码清单同一思路——把「能填什么」变成「只能选什么」。
     * 城市取自 t_school 去重，校区取自 t_school 全量（81 条，前端本地搜索即可）。
     */
    public AdTargetOptions targetOptions() {
        Set<String> cities = new LinkedHashSet<>();
        for (Map<String, Object> row : dictMapper.selectProvinceCity()) {
            Object c = row.get("city");
            if (c != null && !c.toString().isBlank()) {
                cities.add(c.toString().trim());
            }
        }
        return new AdTargetOptions(new ArrayList<>(cities), dictMapper.selectSchools(null, null, null));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createSlot(AdSlot slot) {
        if (slot.getCode() == null || slot.getCode().isBlank()) {
            throw BizException.of("广告位编码不能为空");
        }
        slot.setCode(slot.getCode().trim());
        // 编码必须来自注册表：手填的编码小程序不会去拉，建了等于白建
        AdSlotOption option = AdSlotCatalog.find(slot.getCode())
                .orElseThrow(() -> BizException.of(
                        "广告位编码「" + slot.getCode() + "」小程序不会读取，请从下拉中选择"));
        if (slotMapper.selectCount(Wrappers.<AdSlot>lambdaQuery().eq(AdSlot::getCode, slot.getCode())) > 0) {
            throw BizException.of("该广告位已存在（编码 " + slot.getCode() + " 只能建一个，多条素材建在它下面）");
        }
        // position 由编码唯一决定，不接受前端传值——否则能造出「位置=表白墙、code=home_banner」的矛盾数据
        slot.setPosition(option.getPosition());
        if (slot.getName() == null || slot.getName().isBlank()) {
            slot.setName(option.getName());
        }
        if (slot.getSizeDesc() == null || slot.getSizeDesc().isBlank()) {
            slot.setSizeDesc(option.getSizeHint());
        }
        normalizeTarget(slot);
        assertSlotInScope(slot.getSchoolIds());
        slot.setStatus(slot.getStatus() == null ? 0 : slot.getStatus());
        slot.setAuditStatus(slot.getAuditStatus() == null ? 0 : slot.getAuditStatus());
        slotMapper.insert(slot);
        return slot.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSlot(Long id, AdSlot req) {
        AdSlot exist = requireSlot(id);
        assertSlotInScope(exist.getSchoolIds());

        AdSlot patch = new AdSlot();
        patch.setScopeType(req.getScopeType() != null ? req.getScopeType() : exist.getScopeType());
        // 请求里没带就当没改，避免「只想改排序」把定向清空
        patch.setCity(req.getCity() != null ? req.getCity() : exist.getCity());
        patch.setSchoolIds(req.getSchoolIds() != null ? req.getSchoolIds() : exist.getSchoolIds());
        normalizeTarget(patch);
        assertSlotInScope(patch.getSchoolIds());

        // 必须用 lambdaUpdate 显式 set：updateById 会跳过 null 字段，
        // 「投放范围从校区改回全国」时 school_ids 清不掉，会永久残留在库里。
        slotMapper.update(null, Wrappers.<AdSlot>lambdaUpdate()
                .eq(AdSlot::getId, id)
                .set(req.getName() != null, AdSlot::getName, req.getName())
                .set(AdSlot::getScopeType, patch.getScopeType())
                .set(AdSlot::getCity, patch.getCity())
                .set(AdSlot::getSchoolIds, patch.getSchoolIds())
                .set(req.getSizeDesc() != null, AdSlot::getSizeDesc, req.getSizeDesc())
                .set(req.getMaxCount() != null, AdSlot::getMaxCount, req.getMaxCount())
                .set(req.getSort() != null, AdSlot::getSort, req.getSort())
                .set(req.getStartAt() != null, AdSlot::getStartAt, req.getStartAt())
                .set(req.getEndAt() != null, AdSlot::getEndAt, req.getEndAt())
                .set(req.getRemark() != null, AdSlot::getRemark, req.getRemark())
                // 编码不可改，位置跟着编码走（前端即使传了也忽略）
                .set(AdSlot::getPosition, AdSlotCatalog.find(exist.getCode())
                        .map(AdSlotOption::getPosition)
                        .orElse(exist.getPosition())));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSlot(Long id) {
        AdSlot exist = requireSlot(id);
        assertSlotInScope(exist.getSchoolIds());
        long materials = materialMapper.selectCount(Wrappers.<AdMaterial>lambdaQuery()
                .eq(AdMaterial::getSlotId, id));
        if (materials > 0) {
            throw BizException.of("该广告位下还有 " + materials + " 条素材，请先清空");
        }
        slotMapper.deleteById(id);
    }

    /** 审核：通过后才允许上架；驳回强制下架 */
    @Transactional(rollbackFor = Exception.class)
    public void auditSlot(Long id, boolean pass, String remark) {
        AdSlot exist = requireSlot(id);
        assertSlotInScope(exist.getSchoolIds());
        slotMapper.update(null, Wrappers.<AdSlot>lambdaUpdate()
                .eq(AdSlot::getId, id)
                .set(AdSlot::getAuditStatus, pass ? 1 : 2)
                .set(AdSlot::getAuditRemark, remark)
                .set(!pass, AdSlot::getStatus, 0));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setSlotStatus(Long id, Integer status) {
        AdSlot exist = requireSlot(id);
        assertSlotInScope(exist.getSchoolIds());
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 只能是 0下架 / 1上架");
        }
        if (status == 1 && !Integer.valueOf(1).equals(exist.getAuditStatus())) {
            throw BizException.of("广告位未通过审核，不能上架");
        }
        // 未接入小程序的位一律禁止上架：让「上架成功但一个人都看不到」这种状态根本不存在
        if (status == 1 && !AdSlotCatalog.connected(exist.getCode())) {
            throw BizException.of("该广告位小程序暂未接入，上架后也不会展示");
        }
        slotMapper.update(null, Wrappers.<AdSlot>lambdaUpdate()
                .eq(AdSlot::getId, id)
                .set(AdSlot::getStatus, status));
    }

    // ---------------- 素材 ----------------

    public R.Page<AdMaterial> pageMaterials(Long slotId, String keyword, Integer status, Integer auditStatus,
                                            long page, long size) {
        IPage<AdMaterial> p = materialMapper.selectPage(new Page<>(page, size),
                Wrappers.<AdMaterial>lambdaQuery()
                        .eq(slotId != null, AdMaterial::getSlotId, slotId)
                        .like(keyword != null && !keyword.isBlank(), AdMaterial::getTitle, keyword)
                        .eq(status != null, AdMaterial::getStatus, status)
                        .eq(auditStatus != null, AdMaterial::getAuditStatus, auditStatus)
                        .orderByDesc(AdMaterial::getSort)
                        .orderByDesc(AdMaterial::getId));
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createMaterial(AdMaterial material) {
        if (material.getSlotId() == null) {
            throw BizException.of("必须指定广告位");
        }
        AdSlot slot = requireSlot(material.getSlotId());
        assertSlotInScope(slot.getSchoolIds());
        if (material.getImage() == null || material.getImage().isBlank()) {
            throw BizException.of("素材图片不能为空");
        }
        if (slot.getMaxCount() != null && slot.getMaxCount() > 0) {
            long online = materialMapper.selectCount(Wrappers.<AdMaterial>lambdaQuery()
                    .eq(AdMaterial::getSlotId, slot.getId())
                    .eq(AdMaterial::getStatus, 1));
            if (online >= slot.getMaxCount()) {
                throw BizException.of("该广告位最多同时上架 " + slot.getMaxCount() + " 条素材");
            }
        }
        material.setSlotName(slot.getName());
        material.setStatus(material.getStatus() == null ? 0 : material.getStatus());
        material.setAuditStatus(material.getAuditStatus() == null ? 0 : material.getAuditStatus());
        materialMapper.insert(material);
        return material.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMaterial(Long id, AdMaterial req) {
        AdMaterial exist = requireMaterial(id);
        if (req.getSlotId() != null && !req.getSlotId().equals(exist.getSlotId())) {
            assertSlotInScope(requireSlot(req.getSlotId()).getSchoolIds());
        }
        AdMaterial patch = new AdMaterial();
        patch.setId(id);
        patch.setSlotId(req.getSlotId());
        patch.setTitle(req.getTitle());
        patch.setImage(req.getImage());
        patch.setLinkType(req.getLinkType());
        patch.setLinkValue(req.getLinkValue());
        patch.setSort(req.getSort());
        patch.setStartAt(req.getStartAt());
        patch.setEndAt(req.getEndAt());
        patch.setRemark(req.getRemark());
        materialMapper.updateById(patch);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterial(Long id) {
        if (materialMapper.deleteById(id) == 0) {
            throw BizException.notFound("素材不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditMaterial(Long id, boolean pass, String remark) {
        requireMaterial(id);
        materialMapper.update(null, Wrappers.<AdMaterial>lambdaUpdate()
                .eq(AdMaterial::getId, id)
                .set(AdMaterial::getAuditStatus, pass ? 1 : 2)
                .set(AdMaterial::getAuditRemark, remark)
                .set(!pass, AdMaterial::getStatus, 0));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setMaterialStatus(Long id, Integer status) {
        AdMaterial exist = requireMaterial(id);
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 只能是 0下架 / 1上架");
        }
        if (status == 1) {
            if (!Integer.valueOf(1).equals(exist.getAuditStatus())) {
                throw BizException.of("素材未通过审核，不能上架");
            }
            AdSlot slot = slotMapper.selectById(exist.getSlotId());
            if (slot != null && !Integer.valueOf(1).equals(slot.getStatus())) {
                throw BizException.of("所属广告位未上架");
            }
            if (slot != null && slot.getMaxCount() != null && slot.getMaxCount() > 0) {
                long online = materialMapper.selectCount(Wrappers.<AdMaterial>lambdaQuery()
                        .eq(AdMaterial::getSlotId, exist.getSlotId())
                        .eq(AdMaterial::getStatus, 1));
                if (online >= slot.getMaxCount()) {
                    throw BizException.of("该广告位最多同时上架 " + slot.getMaxCount() + " 条素材");
                }
            }
        }
        materialMapper.update(null, Wrappers.<AdMaterial>lambdaUpdate()
                .eq(AdMaterial::getId, id)
                .set(AdMaterial::getStatus, status));
    }

    // ---------------- 内部 ----------------

    private AdSlot requireSlot(Long id) {
        AdSlot slot = slotMapper.selectById(id);
        if (slot == null) {
            throw BizException.notFound("广告位不存在");
        }
        return slot;
    }

    private AdMaterial requireMaterial(Long id) {
        AdMaterial m = materialMapper.selectById(id);
        if (m == null) {
            throw BizException.notFound("素材不存在");
        }
        return m;
    }

    /**
     * 规整投放范围：去空白、去重、按 scopeType 清掉不相干的那一侧。
     *
     * <p>「清另一侧」是必要的：从「校区」改成「全国」时如果只改 scopeType，
     * 旧的 school_ids 会留在库里，将来再改回校区就带着一批没人记得的旧值。
     */
    private void normalizeTarget(AdSlot slot) {
        slot.setCity(normalizeCsv(slot.getCity()));
        slot.setSchoolIds(normalizeCsv(slot.getSchoolIds()));
        Integer type = slot.getScopeType();
        if (Integer.valueOf(2).equals(type)) {
            if (slot.getCity() == null) {
                throw BizException.of("投放范围为「城市」时，至少要选择一个城市");
            }
            slot.setSchoolIds(null);
        } else if (Integer.valueOf(3).equals(type)) {
            if (slot.getSchoolIds() == null) {
                throw BizException.of("投放范围为「校区」时，至少要选择一个校区");
            }
            slot.setCity(null);
        } else {
            slot.setCity(null);
            slot.setSchoolIds(null);
        }
    }

    /** 逗号分隔列表的规范化：去首尾空格、丢空项、去重、统一用半角逗号 */
    private String normalizeCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Set<String> set = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                set.add(v);
            }
        }
        return set.isEmpty() ? null : String.join(",", set);
    }

    /**
     * 校区级广告位必须全部落在管理员辖区内。
     *
     * <p>用「全部」而不是「任意一个」：辖区管理员把广告投到自己管不到的学校，
     * 等于越权投放，哪怕只多选了一个也应该拒绝。
     */
    private void assertSlotInScope(String schoolIds) {
        DataScope ds = dataScopeResolver.resolve();
        if (ds.isAll() || schoolIds == null || schoolIds.isBlank()) {
            return;
        }
        Set<Long> allowed = new HashSet<>(ds.getSchoolIds());
        for (String part : schoolIds.split(",")) {
            String v = part.trim();
            if (v.isEmpty()) {
                continue;
            }
            long id;
            try {
                id = Long.parseLong(v);
            } catch (NumberFormatException e) {
                throw BizException.of("校区 ID 格式不正确：" + v);
            }
            if (!allowed.contains(id)) {
                throw BizException.forbidden("校区 " + id + " 不在你的管辖范围内");
            }
        }
    }
}
