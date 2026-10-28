package com.campus.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.admin.client.AdminWriteGateway;
import com.campus.admin.client.DemandAdminClient;
import com.campus.admin.config.AdminContext;
import com.campus.admin.config.DataScope;
import com.campus.admin.config.DataScopeResolver;
import com.campus.admin.dto.CommentRow;
import com.campus.admin.dto.DemandRow;
import com.campus.admin.dto.GoodsRow;
import com.campus.admin.mapper.DemandQueryMapper;
import com.campus.admin.dto.PostRow;
import com.campus.admin.dto.WallRow;
import com.campus.admin.entity.ContentAudit;
import com.campus.admin.mapper.CommentQueryMapper;
import com.campus.admin.mapper.ContentAuditMapper;
import com.campus.admin.mapper.GoodsQueryMapper;
import com.campus.admin.mapper.PostQueryMapper;
import com.campus.admin.mapper.WallQueryMapper;
import com.campus.common.search.SearchKit;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容管理：帖子 / 表白墙 / 二手商品 / 需求广场 / 评论 的查询与处置。
 *
 * <p>两条铁律：
 * 1) 后台只读地 join 出展示行，不持有这些业务表的实体；
 *    写一律经 {@link AdminWriteGateway} 打到归属服务，失败才降级直写。
 * 2) 每次处置都落一条 {@link ContentAudit}，内容被物理删除后仍可追溯——
 *    违规处置是会被申诉的，没有流水就等于没有证据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final PostQueryMapper postQueryMapper;
    private final WallQueryMapper wallQueryMapper;
    private final GoodsQueryMapper goodsQueryMapper;
    private final DemandQueryMapper demandQueryMapper;
    private final CommentQueryMapper commentQueryMapper;
    private final ContentAuditMapper contentAuditMapper;
    private final AdminWriteGateway gateway;
    private final DemandAdminClient demandClient;
    private final DataScopeResolver dataScopeResolver;

    /** 需求分类名缓存（type + '-' + id → 中文名）；分类是常量，拉一次就够 */
    private volatile Map<String, String> demandCategoryNames = new HashMap<>();

    // ---------------- 帖子 ----------------

    public R.Page<PostRow> pagePosts(String keyword, Integer status, Long authorId, long page, long size) {
        DataScope ds = dataScopeResolver.resolve();
        List<Long> schoolIds = schoolIdsOrNull(ds);
        if (schoolIds != null && schoolIds.isEmpty()) {
            return R.Page.of(List.of(), 0, page, size);
        }
        IPage<PostRow> p = postQueryMapper.selectPosts(new Page<>(page, size), SearchKit.words(keyword), status, authorId, schoolIds);
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    public PostRow postDetail(Long id) {
        PostRow row = postQueryMapper.selectPost(id);
        if (row == null) {
            throw BizException.notFound("帖子不存在");
        }
        assertInScope(row.getSchoolId());
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public void setPostStatus(Long id, Integer status, String reasonType, String reason) {
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 只能是 0隐藏 / 1正常");
        }
        PostRow row = postDetail(id);
        gateway.setPostStatus(id, status, reason);
        recordAudit("POST", id, row.getTitle(), row.getContent(), row.getImages(),
                row.getAuthorId(), row.getAuthorName(), row.getSchoolId(),
                status == 1 ? "SHOW" : "HIDE", reasonType, reason);
    }

    // ---------------- 表白墙 ----------------

    public R.Page<WallRow> pageWall(String keyword, Integer status, String category, Long authorId,
                                    long page, long size) {
        DataScope ds = dataScopeResolver.resolve();
        List<Long> schoolIds = schoolIdsOrNull(ds);
        if (schoolIds != null && schoolIds.isEmpty()) {
            return R.Page.of(List.of(), 0, page, size);
        }
        IPage<WallRow> p = wallQueryMapper.selectWall(new Page<>(page, size), SearchKit.words(keyword), status, category, authorId, schoolIds);
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    public WallRow wallDetail(Long id) {
        WallRow row = wallQueryMapper.selectWallPost(id);
        if (row == null) {
            throw BizException.notFound("内容不存在");
        }
        assertInScope(row.getSchoolId());
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public void setWallStatus(Long id, Integer status, String reasonType, String reason) {
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 只能是 0隐藏 / 1正常");
        }
        WallRow row = wallDetail(id);
        gateway.setWallStatus(id, status, reason);
        // 匿名内容在这里落真实作者：前台依旧匿名，后台凭这条流水追溯
        recordAudit("WALL", id, null, row.getContent(), row.getImages(),
                row.getRealUserId(), row.getRealNickname(), row.getSchoolId(),
                status == 1 ? "SHOW" : "HIDE", reasonType, reason);
    }

    // ---------------- 二手商品 ----------------

    public R.Page<GoodsRow> pageGoods(String keyword, Integer status, Long sellerId, long page, long size) {
        DataScope ds = dataScopeResolver.resolve();
        List<Long> schoolIds = schoolIdsOrNull(ds);
        if (schoolIds != null && schoolIds.isEmpty()) {
            return R.Page.of(List.of(), 0, page, size);
        }
        IPage<GoodsRow> p = goodsQueryMapper.selectGoods(new Page<>(page, size), SearchKit.words(keyword), status, sellerId, schoolIds);
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    public GoodsRow goodsDetail(Long id) {
        GoodsRow row = goodsQueryMapper.selectGoodsOne(id);
        if (row == null) {
            throw BizException.notFound("商品不存在");
        }
        assertInScope(row.getSchoolId());
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public void setGoodsStatus(Long id, Integer status, String reasonType, String reason) {
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 只能是 0下架 / 1恢复在售");
        }
        GoodsRow row = goodsDetail(id);
        if (Integer.valueOf(2).equals(row.getStatus())) {
            throw BizException.of("商品已售出，不可改回在售");
        }
        gateway.setGoodsStatus(id, status, reason);
        recordAudit("GOODS", id, row.getTitle(), row.getDescription(), row.getImages(),
                row.getSellerId(), row.getSellerName(), row.getSchoolId(),
                status == 1 ? "SHOW" : "HIDE", reasonType, reason);
    }

    // ---------------- 需求广场（求物 / 求帮忙） ----------------

    public R.Page<DemandRow> pageDemands(String keyword, Integer type, Integer status, Long userId,
                                         long page, long size) {
        DataScope ds = dataScopeResolver.resolve();
        List<Long> schoolIds = schoolIdsOrNull(ds);
        if (schoolIds != null && schoolIds.isEmpty()) {
            return R.Page.of(List.of(), 0, page, size);
        }
        IPage<DemandRow> p = demandQueryMapper.selectDemands(new Page<>(page, size),
                SearchKit.words(keyword), type, status, userId, schoolIds);
        p.getRecords().forEach(this::fillDemandNames);
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    public DemandRow demandDetail(Long id) {
        DemandRow row = demandQueryMapper.selectDemandOne(id);
        if (row == null) {
            throw BizException.notFound("需求不存在");
        }
        assertInScope(row.getSchoolId());
        fillDemandNames(row);
        return row;
    }

    /** 需求分类字典：转发 campus-goods 的白名单，后台不自己维护一份 */
    public List<Map<String, Object>> demandCategories() {
        try {
            R<List<Map<String, Object>>> r = demandClient.categories();
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } catch (Exception e) {
            log.warn("[admin] 拉取需求分类失败：{}", e.getMessage());
        }
        return List.of();
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDemandStatus(Long id, Integer status, String reasonType, String reason) {
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 只能是 0下架 / 1恢复进行中");
        }
        DemandRow row = demandDetail(id);
        if (Integer.valueOf(2).equals(row.getStatus())) {
            throw BizException.of("需求已完成，不可改回进行中");
        }
        gateway.setDemandStatus(id, status, reason);
        recordAudit("DEMAND", id, row.getTitle(), row.getDescription(), row.getImages(),
                row.getUserId(), row.getAuthorName(), row.getSchoolId(),
                status == 1 ? "SHOW" : "HIDE", reasonType, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDemand(Long id, String reasonType, String reason) {
        DemandRow row = demandDetail(id);
        gateway.deleteDemand(id, reason);
        recordAudit("DEMAND", id, row.getTitle(), row.getDescription(), row.getImages(),
                row.getUserId(), row.getAuthorName(), row.getSchoolId(),
                "DELETE", reasonType, reason);
    }

    private void fillDemandNames(DemandRow row) {
        if (row == null) {
            return;
        }
        row.setTypeName(row.getType() != null && row.getType() == 2 ? "求帮忙" : "求物");
        row.setCategoryName(demandCategoryNames().get(row.getType() + "-" + row.getCategoryId()));
    }

    /**
     * 分类名缓存。第一次用的时候才去 campus-goods 拉，之后一直复用；
     * 拉不到返回空 Map（页面退化成显示分类 id，不影响列表可用性）。
     */
    private Map<String, String> demandCategoryNames() {
        if (demandCategoryNames.isEmpty()) {
            synchronized (this) {
                if (demandCategoryNames.isEmpty()) {
                    Map<String, String> map = new HashMap<>();
                    for (Map<String, Object> c : demandCategories()) {
                        map.put(c.get("type") + "-" + c.get("id"), String.valueOf(c.get("name")));
                    }
                    demandCategoryNames = map;
                }
            }
        }
        return demandCategoryNames;
    }

    // ---------------- 评论 ----------------

    public R.Page<CommentRow> pageComments(String keyword, String targetType, Long userId, long page, long size) {
        IPage<CommentRow> p = commentQueryMapper.selectComments(new Page<>(page, size), SearchKit.words(keyword), targetType, userId);
        return R.Page.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(String targetType, Long id, Long targetId, String reasonType, String reason) {
        if (targetType == null || (!"P_COMMENT".equals(targetType) && !"W_COMMENT".equals(targetType)
                && !"G_COMMENT".equals(targetType))) {
            throw BizException.of("targetType 只支持 P_COMMENT / W_COMMENT / G_COMMENT");
        }
        gateway.deleteComment(targetType, id, targetId);
        recordAudit(targetType, id, null, reason, null, null, null, null,
                "DELETE", reasonType, reason);
    }

    // ---------------- 审核流水 ----------------

    public R.Page<ContentAudit> pageAudits(String targetType, String action, Long adminId, long page, long size) {
        DataScope ds = dataScopeResolver.resolve();
        List<Long> schoolIds = schoolIdsOrNull(ds);
        Page<ContentAudit> p = new Page<>(page, size);
        IPage<ContentAudit> result = contentAuditMapper.selectPage(p, Wrappers.<ContentAudit>lambdaQuery()
                .eq(targetType != null && !targetType.isBlank(), ContentAudit::getTargetType, targetType)
                .eq(action != null && !action.isBlank(), ContentAudit::getAction, action)
                .eq(adminId != null, ContentAudit::getAdminId, adminId)
                .in(schoolIds != null && !schoolIds.isEmpty(), ContentAudit::getSchoolId, schoolIds)
                // 辖区为空时不能放行，否则负责人能看到全部处置记录
                .apply(schoolIds != null && schoolIds.isEmpty(), "1 = 2")
                .orderByDesc(ContentAudit::getCreatedAt));
        return R.Page.of(result.getRecords(), result.getTotal(), page, size);
    }

    // ---------------- 内部 ----------------

    /**
     * 落一条处置流水。匿名内容（表白墙）在这里存真实作者，
     * 这是「前台匿名 + 后台可追溯」唯一的落点，不能省。
     */
    private void recordAudit(String targetType, Long targetId, String title, String content, String images,
                             Long authorId, String authorName, Long schoolId,
                             String action, String reasonType, String reason) {
        ContentAudit audit = new ContentAudit();
        audit.setTargetType(targetType);
        audit.setTargetId(targetId);
        audit.setTitle(title);
        audit.setContentBrief(content == null ? null : (content.length() <= 200 ? content : content.substring(0, 200)));
        audit.setImages(images);
        audit.setAuthorId(authorId);
        audit.setAuthorName(authorName);
        audit.setSchoolId(schoolId);
        audit.setAction(action);
        audit.setReasonType(reasonType);
        audit.setReason(reason);
        audit.setAdminId(AdminContext.adminId());
        audit.setAdminName(AdminContext.username());
        audit.setSource(1);
        contentAuditMapper.insert(audit);
    }

    /**
     * 数据权限 → SQL 参数。
     *
     * @return null 表示不限校区；空列表表示「一条都看不到」
     */
    private List<Long> schoolIdsOrNull(DataScope ds) {
        return ds.isAll() ? null : ds.getSchoolIds();
    }

    /** 详情页的越权兜底：列表页已经过滤过，但按 id 直查必须再校验一次 */
    private void assertInScope(Long schoolId) {
        DataScope ds = dataScopeResolver.resolve();
        if (ds.isAll()) {
            return;
        }
        if (schoolId == null || !ds.getSchoolIds().contains(schoolId)) {
            throw BizException.forbidden("该内容不在你的管辖范围内");
        }
    }
}
