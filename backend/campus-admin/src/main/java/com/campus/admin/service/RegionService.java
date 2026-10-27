package com.campus.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.admin.dto.AreaOption;
import com.campus.admin.dto.RegionNode;
import com.campus.admin.dto.SchoolOption;
import com.campus.admin.entity.AdminRegion;
import com.campus.admin.entity.AdminRegionLeader;
import com.campus.admin.entity.AdminUser;
import com.campus.admin.mapper.AdminRegionLeaderMapper;
import com.campus.admin.mapper.AdminRegionMapper;
import com.campus.admin.mapper.AdminUserMapper;
import com.campus.admin.mapper.RegionDictMapper;
import com.campus.common.search.SearchKit;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织架构（地区/校区树）与负责人指派。
 *
 * <p>这棵树决定数据权限：管理员被指派为某节点负责人后，
 * data_scope 切成「仅辖区」，其可见数据由该节点展开出的 school_id 集合决定
 * （见 AdminRegionMapper#selectSchoolIdsByRegions）。
 * 所以删除节点前必须确认没有子节点和负责人——删了会让一批管理员瞬间看不到任何数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionService {

    private final AdminRegionMapper regionMapper;
    private final AdminRegionLeaderMapper leaderMapper;
    private final AdminUserMapper adminUserMapper;
    private final RegionDictMapper dictMapper;

    /** 全量树。组织规模是百级，一次查完内存组装，不做递归查库 */
    public List<RegionNode> tree() {
        List<AdminRegion> all = regionMapper.selectList(Wrappers.<AdminRegion>lambdaQuery()
                .orderByDesc(AdminRegion::getSort)
                .orderByAsc(AdminRegion::getId));
        Map<Long, RegionNode> nodeMap = new HashMap<>(all.size() * 2);
        List<RegionNode> roots = new ArrayList<>();
        for (AdminRegion r : all) {
            RegionNode node = new RegionNode();
            node.setId(r.getId());
            node.setParentId(r.getParentId());
            node.setName(r.getName());
            node.setType(r.getType());
            node.setLevel(r.getLevel());
            node.setProvince(r.getProvince());
            node.setCity(r.getCity());
            node.setRefSchoolId(r.getRefSchoolId());
            node.setSort(r.getSort());
            node.setStatus(r.getStatus());
            nodeMap.put(r.getId(), node);
        }
        for (AdminRegion r : all) {
            RegionNode node = nodeMap.get(r.getId());
            RegionNode parent = r.getParentId() == null || r.getParentId() == 0
                    ? null : nodeMap.get(r.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(AdminRegion region) {
        if (region.getName() == null || region.getName().isBlank()) {
            throw BizException.of("名称不能为空");
        }
        if (Integer.valueOf(2).equals(region.getType()) && region.getRefSchoolId() != null) {
            long dup = regionMapper.selectCount(Wrappers.<AdminRegion>lambdaQuery()
                    .eq(AdminRegion::getRefSchoolId, region.getRefSchoolId()));
            if (dup > 0) {
                throw BizException.of("该校区已被其他节点关联");
            }
        }
        if (region.getParentId() == null) {
            region.setParentId(0L);
        }
        if (region.getStatus() == null) {
            region.setStatus(1);
        }
        regionMapper.insert(region);
        return region.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AdminRegion req) {
        AdminRegion exist = requireRegion(id);
        if (id.equals(req.getParentId())) {
            throw BizException.of("上级节点不能是自己");
        }
        AdminRegion patch = new AdminRegion();
        patch.setId(id);
        patch.setParentId(req.getParentId());
        patch.setName(req.getName());
        patch.setType(req.getType());
        patch.setLevel(req.getLevel());
        patch.setProvince(req.getProvince());
        patch.setCity(req.getCity());
        patch.setRefSchoolId(req.getRefSchoolId());
        patch.setSort(req.getSort());
        patch.setStatus(req.getStatus());
        regionMapper.updateById(patch);
        if (exist == null) {
            throw BizException.notFound("节点不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireRegion(id);
        long children = regionMapper.selectCount(Wrappers.<AdminRegion>lambdaQuery()
                .eq(AdminRegion::getParentId, id));
        if (children > 0) {
            throw BizException.of("该节点下还有 " + children + " 个子节点，请先删除子节点");
        }
        long leaders = leaderMapper.selectCount(Wrappers.<AdminRegionLeader>lambdaQuery()
                .eq(AdminRegionLeader::getRegionId, id)
                .eq(AdminRegionLeader::getStatus, 1));
        if (leaders > 0) {
            throw BizException.of("该节点还有 " + leaders + " 位负责人，请先撤销");
        }
        regionMapper.deleteById(id);
    }

    // ---------------- 负责人 ----------------

    public List<AdminRegionLeader> leaders(Long regionId) {
        return leaderMapper.selectList(Wrappers.<AdminRegionLeader>lambdaQuery()
                .eq(regionId != null, AdminRegionLeader::getRegionId, regionId)
                .eq(AdminRegionLeader::getStatus, 1)
                .orderByDesc(AdminRegionLeader::getId));
    }

    /**
     * 指派负责人。指派成功后把该管理员的 data_scope 切成「仅辖区」，
     * 否则他拿了负责人身份却还是看全量数据，权限形同虚设。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long assignLeader(Long regionId, Long adminId, Integer leaderType) {
        requireRegion(regionId);
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null) {
            throw BizException.notFound("管理员不存在");
        }
        if (Integer.valueOf(1).equals(admin.getSuperFlag())) {
            throw BizException.of("超级管理员默认拥有全部数据权限，无需指派");
        }
        long dup = leaderMapper.selectCount(Wrappers.<AdminRegionLeader>lambdaQuery()
                .eq(AdminRegionLeader::getRegionId, regionId)
                .eq(AdminRegionLeader::getAdminUserId, adminId)
                .eq(AdminRegionLeader::getStatus, 1));
        if (dup > 0) {
            throw BizException.of("该管理员已是该节点负责人");
        }
        AdminRegionLeader leader = new AdminRegionLeader();
        leader.setRegionId(regionId);
        leader.setAdminUserId(adminId);
        leader.setLeaderType(leaderType == null ? 1 : leaderType);
        leader.setStatus(1);
        leaderMapper.insert(leader);

        AdminUser patch = new AdminUser();
        patch.setId(adminId);
        patch.setDataScope(2);
        adminUserMapper.updateById(patch);
        return leader.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeLeader(Long leaderId) {
        AdminRegionLeader leader = leaderMapper.selectById(leaderId);
        if (leader == null) {
            throw BizException.notFound("负责人记录不存在");
        }
        leaderMapper.update(null, Wrappers.<AdminRegionLeader>lambdaUpdate()
                .eq(AdminRegionLeader::getId, leaderId)
                .set(AdminRegionLeader::getStatus, 0));

        // 该管理员已经没有任何辖区了，就把数据范围还原成「仅辖区」的空集合语义：
        // 保留 data_scope=2，由 DataScopeResolver 展开成空 → 看不到业务数据，
        // 不会退化成看全量。
        long remain = leaderMapper.selectCount(Wrappers.<AdminRegionLeader>lambdaQuery()
                .eq(AdminRegionLeader::getAdminUserId, leader.getAdminUserId())
                .eq(AdminRegionLeader::getStatus, 1));
        if (remain == 0) {
            log.info("[admin] 管理员 {} 已无辖区，数据范围保持为「仅辖区」（可见数据为空）",
                    leader.getAdminUserId());
        }
    }

    private AdminRegion requireRegion(Long id) {
        AdminRegion region = regionMapper.selectById(id);
        if (region == null) {
            throw BizException.notFound("节点不存在");
        }
        return region;
    }

    // ---------------- 表单选项字典 ----------------

    /**
     * 省 / 市下拉数据。
     *
     * <p>为什么不给前端写死一份行政区划：节点的 province/city 要拿去和 t_school 做
     * <b>等值匹配</b>才算辖区，而学校表里存的是「贵州 / 贵阳」这种不带「省 / 市」后缀的写法，
     * 手输「贵州省」就永远匹配不到任何学校，节点看着正常、数据权限却是空的。
     * 这里直接返回 t_school 现有组合，"能选的就是能匹配的"。
     */
    public List<AreaOption> areas() {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : dictMapper.selectProvinceCity()) {
            Object p = row.get("province");
            if (p == null || p.toString().isBlank()) {
                continue;
            }
            Object c = row.get("city");
            String province = p.toString().trim();
            List<String> cities = grouped.computeIfAbsent(province, k -> new ArrayList<>());
            if (c != null && !c.toString().isBlank()) {
                cities.add(c.toString().trim());
            }
        }
        List<AreaOption> out = new ArrayList<>(grouped.size());
        grouped.forEach((province, cities) -> out.add(new AreaOption(province, cities)));
        return out;
    }

    /** 校区下拉（可按省 / 市过滤，按名称搜索），供「关联校区」选择器使用 */
    public List<SchoolOption> schoolOptions(String province, String city, String keyword) {
        return dictMapper.selectSchools(province, city, SearchKit.words(keyword));
    }
}
