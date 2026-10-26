package com.campus.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.config.AdSlotCatalog;
import com.campus.admin.config.DataScope;
import com.campus.admin.config.DataScopeResolver;
import com.campus.admin.dto.AdSlotOption;
import com.campus.admin.entity.AdMaterial;
import com.campus.admin.entity.AdSlot;
import com.campus.admin.mapper.AdMaterialMapper;
import com.campus.admin.mapper.AdSlotMapper;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    // ---------------- 广告位 ----------------

    public R.Page<AdSlot> pageSlots(String keyword, Integer position, Integer scopeType, Integer status,
                                    long page, long size) {
        DataScope ds = dataScopeResolver.resolve();
        List<Long> schoolIds = ds.isAll() ? null : ds.getSchoolIds();
        IPage<AdSlot> p = slotMapper.selectPage(new Page<>(page, size),
                Wrappers.<AdSlot>lambdaQuery()
                        .like(keyword != null && !keyword.isBlank(), AdSlot::getName, keyword)
                        .eq(position != null, AdSlot::getPosition, position)
                        .eq(scopeType != null, AdSlot::getScopeType, scopeType)
                        .eq(status != null, AdSlot::getStatus, status)
                        .in(schoolIds != null && !schoolIds.isEmpty(), AdSlot::getSchoolId, schoolIds)
                        // 辖区为空：只看全国/城市级广告位，校区级的看不到
                        // apply 是原始 SQL 片段，不经过 XML 转义，这里必须写 <> 而不是 &lt;&gt;
                        .apply(schoolIds != null && schoolIds.isEmpty(), "scope_type <> 3")
                        .orderByDesc(AdSlot::getSort)
                        .orderByDesc(AdSlot::getId));
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    /** 后台下拉用的可选编码；前端拿它渲染「广告位」选择器，不再手填编码 */
    public List<AdSlotOption> slotOptions() {
        return AdSlotCatalog.all();
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
        assertSlotInScope(slot.getSchoolId());
        slot.setStatus(slot.getStatus() == null ? 0 : slot.getStatus());
        slot.setAuditStatus(slot.getAuditStatus() == null ? 0 : slot.getAuditStatus());
        slotMapper.insert(slot);
        return slot.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSlot(Long id, AdSlot req) {
        AdSlot exist = requireSlot(id);
        assertSlotInScope(exist.getSchoolId());
        if (req.getSchoolId() != null) {
            assertSlotInScope(req.getSchoolId());
        }
        AdSlot patch = new AdSlot();
        patch.setId(id);
        patch.setName(req.getName());
        // 编码不可改，位置跟着编码走（前端即使传了也忽略）
        AdSlotCatalog.find(exist.getCode())
                .ifPresent(o -> patch.setPosition(o.getPosition()));
        patch.setScopeType(req.getScopeType());
        patch.setCity(req.getCity());
        patch.setSchoolId(req.getSchoolId());
        patch.setSizeDesc(req.getSizeDesc());
        patch.setMaxCount(req.getMaxCount());
        patch.setSort(req.getSort());
        patch.setStartAt(req.getStartAt());
        patch.setEndAt(req.getEndAt());
        patch.setRemark(req.getRemark());
        slotMapper.updateById(patch);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSlot(Long id) {
        AdSlot exist = requireSlot(id);
        assertSlotInScope(exist.getSchoolId());
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
        assertSlotInScope(exist.getSchoolId());
        slotMapper.update(null, Wrappers.<AdSlot>lambdaUpdate()
                .eq(AdSlot::getId, id)
                .set(AdSlot::getAuditStatus, pass ? 1 : 2)
                .set(AdSlot::getAuditRemark, remark)
                .set(!pass, AdSlot::getStatus, 0));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setSlotStatus(Long id, Integer status) {
        AdSlot exist = requireSlot(id);
        assertSlotInScope(exist.getSchoolId());
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
        assertSlotInScope(slot.getSchoolId());
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
            assertSlotInScope(requireSlot(req.getSlotId()).getSchoolId());
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

    /** 校区级广告位必须落在管理员辖区内 */
    private void assertSlotInScope(Long schoolId) {
        DataScope ds = dataScopeResolver.resolve();
        if (ds.isAll() || schoolId == null) {
            return;
        }
        if (!ds.getSchoolIds().contains(schoolId)) {
            throw BizException.forbidden("该校区不在你的管辖范围内");
        }
    }
}
