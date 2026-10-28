package com.campus.goods.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.api.R;
import com.campus.common.client.SecurityGuard;
import com.campus.common.client.UserClient;
import com.campus.common.client.dto.UserBrief;
import com.campus.common.content.Visibility;
import com.campus.common.exception.BizException;
import com.campus.common.search.SearchKit;
import com.campus.common.security.UserContext;
import com.campus.common.security.WriteRatePolicy;
import com.campus.goods.dto.DemandReplyReq;
import com.campus.goods.dto.DemandReq;
import com.campus.goods.entity.Demand;
import com.campus.goods.entity.DemandReply;
import com.campus.goods.mapper.DemandMapper;
import com.campus.goods.mapper.DemandReplyMapper;
import com.campus.goods.mapper.SchoolRegionMapper;
import com.campus.goods.mapper.UserSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 校园需求广场（求物 / 求帮忙）。
 *
 * <p>与 {@link GoodsService} 的关系：查询骨架（可见范围过滤、多词搜索、作者快照刷新）
 * 完全同构，但字段与状态机是反的，所以两张表、两套 Service，不做混存。
 *
 * <p>红线：本模块只做信息展示与连接，不接支付、不做担保、不做评价打分。
 * 一旦出现评价体系，它就从「互助信息栏」变成「服务平台」，类目与资质要求完全不同。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemandService {

    private final DemandMapper demandMapper;
    /** UGC 写入频控：求物/求帮忙与回应都算写入 */
    private final WriteRatePolicy writeRate;
    private final DemandReplyMapper replyMapper;
    private final SchoolRegionMapper schoolRegionMapper;
    private final UserSnapshotMapper userSnapshotMapper;
    private final UserClient userClient;
    private final SecurityGuard securityGuard;

    public record DemandCategoryVO(Integer type, Integer id, String name, String icon) {
    }

    /**
     * 需求分类白名单（同时是展示顺序的唯一数据源，前端不再自己维护一份）。
     *
     * <p>两套 id 各自从 1 开始，因为它们是 type 下的二级分类 ——
     * 「求物的 1」是数码，「求帮忙的 1」是代取代办。
     * 所以任何按 category_id 查询的地方都必须同时带 type，否则一定串类。
     *
     * <p>id 只能往后追加、不能重排：存量需求已落库 category_id。
     */
    private static final List<DemandCategoryVO> CATEGORIES = List.of(
            new DemandCategoryVO(1, 1, "数码", "📱"),
            new DemandCategoryVO(1, 2, "图书教材", "📚"),
            new DemandCategoryVO(1, 3, "生活用品", "🧺"),
            new DemandCategoryVO(1, 4, "服饰", "👕"),
            new DemandCategoryVO(1, 5, "运动", "🏀"),
            new DemandCategoryVO(1, 6, "代步", "🚲"),
            new DemandCategoryVO(1, 7, "其他", "✨"),
            new DemandCategoryVO(2, 1, "代取代办", "📦"),
            new DemandCategoryVO(2, 2, "学习互助", "✏️"),
            new DemandCategoryVO(2, 3, "技术帮忙", "🛠️"),
            new DemandCategoryVO(2, 4, "组队拼车", "🤝"),
            new DemandCategoryVO(2, 5, "寻物寻人", "🔍"),
            new DemandCategoryVO(2, 6, "其他", "✨"));

    /** type 为空返回全部分类（后台下拉用） */
    public List<DemandCategoryVO> categories(Integer type) {
        if (type == null) {
            return CATEGORIES;
        }
        return CATEGORIES.stream().filter(c -> type.equals(c.type())).toList();
    }

    public String categoryName(Integer type, Integer categoryId) {
        if (type == null || categoryId == null) {
            return "其他";
        }
        return CATEGORIES.stream()
                .filter(c -> type.equals(c.type()) && categoryId.equals(c.id()))
                .map(DemandCategoryVO::name)
                .findFirst()
                .orElse("其他");
    }

    /** 学校所在市（未配置/未知时为 null） */
    public String cityOfSchool(Long schoolId) {
        return schoolId == null ? null : schoolRegionMapper.selectCityBySchoolId(schoolId);
    }

    /**
     * 需求分页。
     *
     * <p>userId 非空（个人主页场景）：返回该作者全部状态需求，不加 status 与范围过滤；
     * 否则只返回进行中（status=1）并按范围过滤 —— 已完成/已下架不该占着广场。
     */
    public R.Page<Demand> page(String keyword, Integer type, Integer categoryId,
                               long page, long size, Long schoolId, String city, Long userId) {
        LambdaQueryWrapper<Demand> w = Wrappers.<Demand>lambdaQuery();
        if (userId != null) {
            w.eq(Demand::getUserId, userId);
        } else {
            w.eq(Demand::getStatus, Demand.STATUS_OPEN);
            if (StringUtils.hasText(city)) {
                List<Long> citySchoolIds = schoolRegionMapper.selectSchoolIdsByCity(city.trim());
                if (citySchoolIds.isEmpty()) {
                    return R.Page.of(List.of(), 0, page, size);
                }
                w.in(Demand::getSchoolId, citySchoolIds);
                // 可见范围：同城他校的需求，只有对方主动放开到「同市」才进流；
                // 本校需求永远可见（本校 ⊂ 本市）。
                // 分成两个分支写而不是用 eq(boolean, ...)：MP 的 or() 会先追加关键字，
                // 后面条件不成立时会留下悬空的 OR，直接把整条 SQL 打成语法错误。
                if (schoolId != null && schoolId > 0) {
                    w.and(q -> q.eq(Demand::getVisibility, Visibility.CITY)
                            .or().eq(Demand::getSchoolId, schoolId));
                } else {
                    w.eq(Demand::getVisibility, Visibility.CITY);
                }
            } else if (schoolId != null && schoolId > 0) {
                w.eq(Demand::getSchoolId, schoolId);
            }
        }
        if (type != null && type > 0) {
            w.eq(Demand::getType, type);
        }
        // 多词搜索：与闲置同一套 SearchKit 规则（词间 AND，同词多列 OR）
        SearchKit.andAllWords(w, keyword, Demand::getTitle, Demand::getDescription);
        if (categoryId != null && categoryId > 0) {
            w.eq(Demand::getCategoryId, categoryId);
        }
        w.orderByDesc(Demand::getCreatedAt);
        Page<Demand> p = demandMapper.selectPage(new Page<>(page, size), w);
        p.getRecords().forEach(this::fillExtra);
        refreshAuthors(p.getRecords());
        return R.Page.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public List<Demand> mine(Long userId, Integer status) {
        List<Demand> list = demandMapper.selectList(Wrappers.<Demand>lambdaQuery()
                .eq(Demand::getUserId, userId)
                .eq(status != null, Demand::getStatus, status)
                .orderByDesc(Demand::getCreatedAt));
        list.forEach(this::fillExtra);
        return list;
    }

    /**
     * 可见范围校验：仅本校的需求，外校用户看不到。
     *
     * <p>光在列表里过滤不够 —— 详情接口是独立入口，分享链接可以直达。
     * 用 404 而不是 403：不暴露「这条内容存在但你没权限」。
     */
    private void assertVisible(Demand demand, Long userId) {
        if (Visibility.isCity(demand.getVisibility())) {
            return;
        }
        if (demand.getSchoolId() == null) {
            return;
        }
        if (userId != null && userId.equals(demand.getUserId())) {
            return;
        }
        Long viewerSchool = UserContext.schoolId();
        if (viewerSchool == null || viewerSchool.equals(demand.getSchoolId())) {
            return;
        }
        throw BizException.notFound("需求不存在或已被删除");
    }

    public Demand detail(Long id, Long userId) {
        Demand demand = demandMapper.selectById(id);
        if (demand == null) {
            throw BizException.notFound("需求不存在或已被删除");
        }
        assertVisible(demand, userId);
        demandMapper.incrView(id);
        demand.setViewCount((demand.getViewCount() == null ? 0 : demand.getViewCount()) + 1);
        fillExtra(demand);
        refreshAuthors(List.of(demand));
        demand.setMine(userId != null && userId.equals(demand.getUserId()));
        demand.setReplied(userId != null && hasReplied(id, userId));
        return demand;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long publish(DemandReq req) {
        Long userId = UserContext.requireVerifiedUserId();
        writeRate.check(WriteRatePolicy.Action.PUBLISH_DEMAND, userId);
        Long schoolId = UserContext.schoolId();
        if (schoolId == null) {
            throw new BizException("请先完成校园身份认证");
        }
        Demand demand = new Demand();
        apply(demand, req);
        demand.setUserId(userId);
        demand.setSchoolId(schoolId);
        fillAuthor(demand, userId);
        String title = securityGuard.purify(userId,
                demand.getTitle() == null ? "" : demand.getTitle(), SecurityGuard.BIZ_DEMAND);
        String description = securityGuard.purify(userId,
                demand.getDescription() == null ? "" : demand.getDescription(), SecurityGuard.BIZ_DEMAND);
        demand.setTitle(title);
        demand.setDescription(description);
        securityGuard.checkText(userId, title + "\n" + description,
                SecurityGuard.SCENE_FORUM, SecurityGuard.BIZ_DEMAND);
        demand.setStatus(Demand.STATUS_OPEN);
        demand.setViewCount(0);
        demand.setReplyCount(0);
        demandMapper.insert(demand);
        securityGuard.checkImages(userId, SecurityGuard.BIZ_DEMAND, demand.getId(), req.getImages());
        return demand.getId();
    }

    public void update(Long id, DemandReq req) {
        Long userId = UserContext.requireUserId();
        Demand demand = requireOwner(id, userId);
        if (demand.getStatus() != null && demand.getStatus() == Demand.STATUS_DONE) {
            throw new BizException("已完成的需求不能修改");
        }
        apply(demand, req);
        String title = securityGuard.purify(userId,
                demand.getTitle() == null ? "" : demand.getTitle(), SecurityGuard.BIZ_DEMAND);
        String description = securityGuard.purify(userId,
                demand.getDescription() == null ? "" : demand.getDescription(), SecurityGuard.BIZ_DEMAND);
        demand.setTitle(title);
        demand.setDescription(description);
        securityGuard.checkText(userId, title + "\n" + description,
                SecurityGuard.SCENE_FORUM, SecurityGuard.BIZ_DEMAND);
        securityGuard.checkImages(userId, SecurityGuard.BIZ_DEMAND, id, req.getImages());
        demandMapper.updateById(demand);
    }

    /** 改状态：0下架 / 1重新开放 / 2已完成。发布者本人操作。 */
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BizException("状态不合法");
        }
        Demand demand = requireOwner(id, UserContext.requireUserId());
        demand.setStatus(status);
        demandMapper.updateById(demand);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOwner(id, UserContext.requireUserId());
        demandMapper.deleteById(id);
        // 需求没了，挂在它下面的回应也没有意义，一并清掉（否则留下孤儿行，
        // 且「我发出的回应」列表会指向一个 404 的需求）
        replyMapper.delete(Wrappers.<DemandReply>lambdaQuery().eq(DemandReply::getDemandId, id));
    }

    /* ================= 回应 ================= */

    /**
     * 提交回应（我有货 / 我能帮）。
     *
     * <p>一人一需求只能有一条：重复提交会把发布者的列表刷成同一个人，
     * 也让人误以为有很多人响应。想改内容请让发布者忽略后重发，或直接走私信。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long reply(Long demandId, DemandReplyReq req) {
        Long userId = UserContext.requireVerifiedUserId();
        writeRate.check(WriteRatePolicy.Action.REPLY, userId);
        Demand demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw BizException.notFound("需求不存在或已被删除");
        }
        if (demand.getUserId().equals(userId)) {
            throw new BizException("这是你自己发布的需求");
        }
        if (demand.getStatus() == null || demand.getStatus() != Demand.STATUS_OPEN) {
            throw new BizException("该需求已结束");
        }
        if (hasReplied(demandId, userId)) {
            throw new BizException("你已经回应过这条需求了");
        }
        String message = req.getMessage() == null ? "" : req.getMessage().trim();
        if (!StringUtils.hasText(message)) {
            throw new BizException("说点什么，让对方知道你能提供什么");
        }
        String clean = securityGuard.purify(userId, message, SecurityGuard.BIZ_DEMAND_REPLY);
        securityGuard.checkText(userId, clean, SecurityGuard.SCENE_COMMENT,
                SecurityGuard.BIZ_DEMAND_REPLY);

        DemandReply reply = new DemandReply();
        reply.setDemandId(demandId);
        reply.setReplierId(userId);
        reply.setReplierName(nicknameOf(userId));
        reply.setMessage(clean);
        reply.setContact(StringUtils.hasText(req.getContact()) ? req.getContact().trim() : null);
        reply.setStatus(DemandReply.STATUS_NEW);
        replyMapper.insert(reply);
        demandMapper.incrReply(demandId, 1);
        return reply.getId();
    }

    /**
     * 某条需求的回应列表（只有发布者能看 —— 里面带联系方式明文）。
     * 顺手把未读的置为已读。
     */
    public List<DemandReply> replies(Long demandId, Long userId) {
        Demand demand = demandMapper.selectById(demandId);
        if (demand == null) {
            throw BizException.notFound("需求不存在或已被删除");
        }
        if (!demand.getUserId().equals(userId)) {
            throw BizException.forbidden("只能查看自己收到的回应");
        }
        List<DemandReply> list = replyMapper.selectList(Wrappers.<DemandReply>lambdaQuery()
                .eq(DemandReply::getDemandId, demandId)
                .orderByDesc(DemandReply::getCreatedAt));
        refreshRepliers(list);
        List<Long> unread = list.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == DemandReply.STATUS_NEW)
                .map(DemandReply::getId)
                .toList();
        if (!unread.isEmpty()) {
            replyMapper.update(null, Wrappers.<DemandReply>lambdaUpdate()
                    .set(DemandReply::getStatus, DemandReply.STATUS_READ)
                    .in(DemandReply::getId, unread));
            list.forEach(r -> {
                if (r.getStatus() != null && r.getStatus() == DemandReply.STATUS_NEW) {
                    r.setStatus(DemandReply.STATUS_READ);
                }
            });
        }
        return list;
    }

    /** 我发出的回应（带需求标题，方便回溯） */
    public List<DemandReply> myReplies(Long userId) {
        List<DemandReply> list = replyMapper.selectList(Wrappers.<DemandReply>lambdaQuery()
                .eq(DemandReply::getReplierId, userId)
                .orderByDesc(DemandReply::getCreatedAt));
        if (list.isEmpty()) {
            return list;
        }
        Map<Long, Demand> demands = demandMapper
                .selectBatchIds(list.stream().map(DemandReply::getDemandId).distinct().toList())
                .stream().collect(Collectors.toMap(Demand::getId, d -> d, (a, b) -> a));
        list.forEach(r -> {
            Demand d = demands.get(r.getDemandId());
            if (d != null) {
                r.setDemandTitle(d.getTitle());
                r.setDemandType(d.getType());
            }
        });
        return list;
    }

    // ---------------- 内部方法 ----------------

    private void apply(Demand demand, DemandReq req) {
        demand.setType(req.getType() == null ? Demand.TYPE_WANT : req.getType());
        demand.setTitle(req.getTitle().trim());
        demand.setDescription(req.getDescription());
        demand.setCategoryId(req.getCategoryId() == null ? 0 : req.getCategoryId());
        demand.setBudget(req.getBudget());
        demand.setExpectTime(trimToNull(req.getExpectTime()));
        demand.setLocation(trimToNull(req.getLocation()));
        demand.setVisibility(Visibility.normalize(req.getVisibility()));
        if (req.getImages() != null) {
            demand.setImages(req.getImages().stream()
                    .filter(StringUtils::hasText).limit(9).collect(Collectors.joining(",")));
        }
    }

    private String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private Demand requireOwner(Long id, Long userId) {
        Demand demand = demandMapper.selectById(id);
        if (demand == null) {
            throw BizException.notFound("需求不存在");
        }
        if (!demand.getUserId().equals(userId)) {
            throw BizException.forbidden("只能操作自己发布的需求");
        }
        return demand;
    }

    private boolean hasReplied(Long demandId, Long userId) {
        return replyMapper.selectCount(Wrappers.<DemandReply>lambdaQuery()
                .eq(DemandReply::getDemandId, demandId)
                .eq(DemandReply::getReplierId, userId)) > 0;
    }

    private void fillAuthor(Demand demand, Long userId) {
        try {
            R<UserBrief> res = userClient.getBrief(userId);
            if (res != null && res.getData() != null) {
                demand.setAuthorName(res.getData().getNickname());
                demand.setAuthorAvatar(res.getData().getAvatar());
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败 userId={}, {}", userId, e.getMessage());
        }
        if (!StringUtils.hasText(demand.getAuthorName())) {
            demand.setAuthorName("校园用户");
        }
    }

    private String nicknameOf(Long userId) {
        try {
            R<UserBrief> res = userClient.getBrief(userId);
            if (res != null && res.getData() != null && StringUtils.hasText(res.getData().getNickname())) {
                return res.getData().getNickname();
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败 userId={}", userId);
        }
        return "校园用户";
    }

    /**
     * 用 t_user 的最新昵称/头像覆盖需求里的作者快照 —— 与闲置同一做法。
     * 作者改名后列表仍显示旧名字会与「我的」页对不上。
     */
    private void refreshAuthors(List<Demand> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> userIds = list.stream().map(Demand::getUserId)
                .filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return;
        }
        try {
            Map<Long, UserBrief> briefs = userSnapshotMapper.selectBriefByIds(userIds).stream()
                    .collect(Collectors.toMap(UserBrief::getId, u -> u, (a, b) -> a));
            for (Demand d : list) {
                UserBrief brief = briefs.get(d.getUserId());
                if (brief == null) {
                    continue;
                }
                if (StringUtils.hasText(brief.getNickname())) {
                    d.setAuthorName(brief.getNickname());
                }
                if (StringUtils.hasText(brief.getAvatar())) {
                    d.setAuthorAvatar(brief.getAvatar());
                }
            }
        } catch (Exception e) {
            log.warn("刷新需求作者资料失败，沿用快照: {}", e.getMessage());
        }
    }

    private void refreshRepliers(List<DemandReply> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> userIds = list.stream().map(DemandReply::getReplierId)
                .filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return;
        }
        try {
            Map<Long, UserBrief> briefs = userSnapshotMapper.selectBriefByIds(userIds).stream()
                    .collect(Collectors.toMap(UserBrief::getId, u -> u, (a, b) -> a));
            for (DemandReply r : list) {
                UserBrief brief = briefs.get(r.getReplierId());
                if (brief == null) {
                    continue;
                }
                if (StringUtils.hasText(brief.getNickname())) {
                    r.setReplierName(brief.getNickname());
                }
                if (StringUtils.hasText(brief.getAvatar())) {
                    r.setReplierAvatar(brief.getAvatar());
                }
            }
        } catch (Exception e) {
            log.warn("刷新回应者资料失败，沿用快照: {}", e.getMessage());
        }
    }

    private void fillExtra(Demand demand) {
        if (StringUtils.hasText(demand.getImages())) {
            demand.setImageList(Arrays.stream(demand.getImages().split(","))
                    .filter(StringUtils::hasText).toList());
        } else {
            demand.setImageList(List.of());
        }
        demand.setCategoryName(categoryName(demand.getType(), demand.getCategoryId()));
        demand.setTypeName(demand.getType() != null && demand.getType() == Demand.TYPE_HELP
                ? "求帮忙" : "求物");
    }
}
