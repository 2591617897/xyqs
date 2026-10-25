package com.campus.goods.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.api.R;
import com.campus.common.client.FollowClient;
import com.campus.common.client.NotificationClient;
import com.campus.common.client.SecurityGuard;
import com.campus.common.client.UserClient;
import com.campus.common.client.dto.NotifyReq;
import com.campus.common.client.dto.UserBrief;
import com.campus.common.exception.BizException;
import com.campus.common.security.UserContext;
import com.campus.common.social.Like;
import com.campus.common.social.mapper.LikeMapper;
import com.campus.goods.dto.GoodsOrderReq;
import com.campus.goods.dto.GoodsReq;
import com.campus.goods.entity.Goods;
import com.campus.goods.entity.GoodsComment;
import com.campus.goods.entity.GoodsOrder;
import com.campus.goods.mapper.GoodsCommentMapper;
import com.campus.goods.mapper.GoodsMapper;
import com.campus.goods.mapper.GoodsOrderMapper;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService {

    private static final String TARGET = "GOODS";

    public record CategoryVO(Integer id, String name, String icon) {
    }

    private static final List<CategoryVO> CATEGORIES = List.of(
            new CategoryVO(1, "数码", "📱"),
            new CategoryVO(2, "图书", "📚"),
            new CategoryVO(3, "生活", "🧺"),
            new CategoryVO(4, "服饰", "👕"),
            new CategoryVO(5, "运动", "🏀"),
            new CategoryVO(6, "其他", "✨"));

    private final GoodsMapper goodsMapper;
    private final GoodsOrderMapper orderMapper;
    private final GoodsCommentMapper commentMapper;
    private final SchoolRegionMapper schoolRegionMapper;
    private final UserSnapshotMapper userSnapshotMapper;
    private final LikeMapper likeMapper;
    private final UserClient userClient;
    private final FollowClient followClient;
    private final NotificationClient notificationClient;
    private final SecurityGuard securityGuard;

    public List<CategoryVO> categories() {
        return CATEGORIES;
    }

    /** 学校所在市（未配置/未知时为 null） */
    public String cityOfSchool(Long schoolId) {
        return schoolId == null ? null : schoolRegionMapper.selectCityBySchoolId(schoolId);
    }

    /**
     * 商品分页。
     * userId 非空（个人主页场景）：返回该作者全部状态商品，不加 status=1 与学校范围过滤；
     * 否则按范围过滤：city 非空按同城高校混排，schoolId 非空按本校。
     */
    public R.Page<Goods> page(String keyword, Integer categoryId, String sort,
                              long page, long size, Long schoolId, String city, Long userId) {
        LambdaQueryWrapper<Goods> w = Wrappers.<Goods>lambdaQuery();
        if (userId != null) {
            // 个人主页：作者全部状态（前端渲染状态标签）
            w.eq(Goods::getUserId, userId);
        } else {
            w.eq(Goods::getStatus, 1);
            if (StringUtils.hasText(city)) {
                List<Long> citySchoolIds = schoolRegionMapper.selectSchoolIdsByCity(city.trim());
                if (citySchoolIds.isEmpty()) {
                    return R.Page.of(List.of(), 0, page, size);
                }
                w.in(Goods::getSchoolId, citySchoolIds);
            } else if (schoolId != null && schoolId > 0) {
                w.eq(Goods::getSchoolId, schoolId);
            }
        }
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword));
        }
        if (categoryId != null && categoryId > 0) {
            w.eq(Goods::getCategoryId, categoryId);
        }
        switch (sort == null ? "latest" : sort) {
            case "price_asc" -> w.orderByAsc(Goods::getPrice);
            case "price_desc" -> w.orderByDesc(Goods::getPrice);
            case "hot" -> w.orderByDesc(Goods::getViewCount);
            default -> w.orderByDesc(Goods::getCreatedAt);
        }
        Page<Goods> p = goodsMapper.selectPage(new Page<>(page, size), w);
        p.getRecords().forEach(this::fillExtra);
        refreshSellers(p.getRecords());
        return R.Page.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /**
     * 内部计数：该作者商品数（status 可空 = 全部；已物理删除的不计）。
     * 供个人主页「闲置在售 / 累计发布」聚合，campus-user Feign 调用。
     */
    public long countByUser(Long userId, Integer status) {
        Long cnt = goodsMapper.selectCount(Wrappers.<Goods>lambdaQuery()
                .eq(Goods::getUserId, userId)
                .eq(status != null, Goods::getStatus, status));
        return cnt == null ? 0 : cnt;
    }

    /** 该作者商品收藏数（t_like target=GOODS） */
    public long countFavorites(Long userId) {
        Long cnt = likeMapper.selectCount(Wrappers.<Like>lambdaQuery()
                .eq(Like::getTargetType, TARGET)
                .eq(Like::getUserId, userId));
        return cnt == null ? 0 : cnt;
    }

    /** 该作者订单数：role=buyer/seller，status 可选（不传 = 全部） */
    public long countOrders(Long userId, String role, Integer status) {
        Long cnt = orderMapper.selectCount(Wrappers.<GoodsOrder>lambdaQuery()
                .eq("seller".equalsIgnoreCase(role), GoodsOrder::getSellerId, userId)
                .eq(!"seller".equalsIgnoreCase(role), GoodsOrder::getBuyerId, userId)
                .eq(status != null, GoodsOrder::getStatus, status));
        return cnt == null ? 0 : cnt;
    }

    public Goods detail(Long id, Long userId) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) {
            throw BizException.notFound("商品不存在或已被删除");
        }
        goodsMapper.incrView(id);
        int views = goods.getViewCount() == null ? 0 : goods.getViewCount();
        goods.setViewCount(views + 1);
        fillExtra(goods);
        fillSellerStats(goods);
        refreshSellers(List.of(goods));
        if (userId != null) {
            goods.setLiked(hasLiked(id, userId));
        }
        return goods;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long publish(GoodsReq req) {
        Long userId = UserContext.requireVerifiedUserId();
        Long schoolId = UserContext.schoolId();
        if (schoolId == null) {
            throw new BizException("请先完成校园身份认证");
        }
        Goods goods = new Goods();
        apply(goods, req);
        goods.setUserId(userId);
        goods.setSchoolId(schoolId);
        fillSeller(goods, userId);
        securityGuard.checkText(userId,
                (req.getTitle() == null ? "" : req.getTitle()) + "\n" + (req.getDescription() == null ? "" : req.getDescription()),
                SecurityGuard.SCENE_FORUM, SecurityGuard.BIZ_GOODS);
        goods.setStatus(1);
        goods.setViewCount(0);
        goods.setLikeCount(0);
        goodsMapper.insert(goods);
        // 闲置图最多 9 张，一次性提交异步送审（先发后审，结论走回调）
        securityGuard.checkImages(userId, SecurityGuard.BIZ_GOODS, goods.getId(), req.getImages());
        return goods.getId();
    }

    public void update(Long id, GoodsReq req) {
        Goods goods = requireOwner(id, UserContext.requireUserId());
        if (goods.getStatus() != null && goods.getStatus() == 2) {
            throw new BizException("已售出的商品不能修改");
        }
        apply(goods, req);
        securityGuard.checkText(UserContext.requireUserId(),
                (req.getTitle() == null ? "" : req.getTitle()) + "\n" + (req.getDescription() == null ? "" : req.getDescription()),
                SecurityGuard.SCENE_FORUM, SecurityGuard.BIZ_GOODS);
        securityGuard.checkImages(UserContext.requireUserId(), SecurityGuard.BIZ_GOODS, id,
                req.getImages());
        goodsMapper.updateById(goods);
    }

    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BizException("状态不合法");
        }
        Goods goods = requireOwner(id, UserContext.requireUserId());
        goods.setStatus(status);
        goodsMapper.updateById(goods);
    }

    public void delete(Long id) {
        requireOwner(id, UserContext.requireUserId());
        goodsMapper.deleteById(id);
        likeMapper.delete(Wrappers.<Like>lambdaQuery()
                .eq(Like::getTargetType, TARGET).eq(Like::getTargetId, id));
    }

    public List<Goods> mine(Long userId, Integer status) {
        List<Goods> list = goodsMapper.selectList(Wrappers.<Goods>lambdaQuery()
                .eq(Goods::getUserId, userId)
                .eq(status != null, Goods::getStatus, status)
                .orderByDesc(Goods::getCreatedAt));
        list.forEach(this::fillExtra);
        return list;
    }

    /**
     * 收藏/取消收藏，返回最新状态 (true = 已收藏)
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long goodsId, Long userId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw BizException.notFound("商品不存在");
        }
        Like exist = likeMapper.selectOne(Wrappers.<Like>lambdaQuery()
                .eq(Like::getTargetType, TARGET)
                .eq(Like::getTargetId, goodsId)
                .eq(Like::getUserId, userId));
        if (exist != null) {
            likeMapper.deleteById(exist.getId());
            goodsMapper.incrLike(goodsId, -1);
            return false;
        }
        Like like = new Like();
        like.setTargetType(TARGET);
        like.setTargetId(goodsId);
        like.setUserId(userId);
        likeMapper.insert(like);
        goodsMapper.incrLike(goodsId, 1);
        tryNotifyLikeGoods(goods, userId);
        return true;
    }

    private void tryNotifyLikeGoods(Goods goods, Long actorId) {
        Long receiverId = goods.getUserId();
        if (receiverId == null || receiverId.equals(actorId)) return;
        NotifyReq req = new NotifyReq();
        req.setType("LIKE_GOODS");
        req.setReceiverId(receiverId);
        req.setActorId(actorId);
        req.setTargetType("GOODS");
        req.setTargetId(goods.getId());
        req.setContentSnapshot(goods.getTitle());
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { notificationClient.send(req); }
            catch (Exception e) { log.warn("[notify] likeGoods failed goodsId={}: {}", goods.getId(), e.getMessage()); }
        });
    }

    /* ================= 评论（含楼中楼回复） ================= */

    /** 商品评论列表：内存组装两层树（顶层评论 + 各自回复） */
    public List<GoodsComment> comments(Long goodsId, Long userId) {
        List<GoodsComment> all = commentMapper.selectList(Wrappers.<GoodsComment>lambdaQuery()
                .eq(GoodsComment::getGoodsId, goodsId)
                .orderByAsc(GoodsComment::getCreatedAt)
                .last("limit 500"));
        Map<Long, List<GoodsComment>> replyMap = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(GoodsComment::getParentId));
        return all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .map(c -> {
                    c.setMine(userId != null && userId.equals(c.getUserId()));
                    c.setImageList(splitImages(c.getImages()));
                    List<GoodsComment> replies = replyMap.getOrDefault(c.getId(), List.of());
                    replies.forEach(r -> {
                        r.setMine(userId != null && userId.equals(r.getUserId()));
                        r.setImageList(splitImages(r.getImages()));
                    });
                    c.setReplies(replies);
                    c.setReplyCount(replies.size());
                    return c;
                })
                .toList();
    }

    /** 发表评论 / 回复（parentId>0 时为楼中楼回复） */
    @Transactional(rollbackFor = Exception.class)
    public Long addComment(Long goodsId, String content, List<String> images, Long parentId, List<Long> mentionUserIds) {
        Long userId = UserContext.requireVerifiedUserId();
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw BizException.notFound("商品不存在");
        }
        String text = content == null ? "" : content.trim();
        String imgs = joinImages(images);
        if (!StringUtils.hasText(text) && !StringUtils.hasText(imgs)) {
            throw new BizException("说点什么，或者配张图吧");
        }
        securityGuard.checkText(userId, text, SecurityGuard.SCENE_COMMENT,
                SecurityGuard.BIZ_GOODS_COMMENT);
        GoodsComment comment = new GoodsComment();
        comment.setGoodsId(goodsId);
        comment.setUserId(userId);
        comment.setParentId(parentId == null ? 0L : parentId);
        GoodsComment parentComment = null;
        if (comment.getParentId() > 0) {
            parentComment = commentMapper.selectById(comment.getParentId());
            if (parentComment == null || !goodsId.equals(parentComment.getGoodsId())) {
                throw new BizException("要回复的评论不存在");
            }
            // 楼中楼拍平：对二级回复再回复时，挂到其顶层评论下。
            // 否则会生成三级评论，而列表只组装两层树，三级永远不显示。
            if (parentComment.getParentId() != null && parentComment.getParentId() > 0) {
                comment.setParentId(parentComment.getParentId());
            }
            comment.setReplyToName(StringUtils.hasText(parentComment.getUserName()) ? parentComment.getUserName() : "校园用户");
        }
        comment.setContent(text);
        comment.setImages(imgs);
        try {
            R<UserBrief> res = userClient.getBrief(userId);
            if (res != null && res.getData() != null) {
                comment.setUserName(res.getData().getNickname());
                comment.setUserAvatar(res.getData().getAvatar());
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败 userId={}", userId);
        }
        comment.setUserName(comment.getUserName() == null ? "校园用户" : comment.getUserName());
        commentMapper.insert(comment);
        securityGuard.checkImages(userId, SecurityGuard.BIZ_GOODS_COMMENT, comment.getId(), images);
        goodsMapper.incrComment(goodsId, 1);
        comment.setMine(true);
        comment.setImageList(splitImages(imgs));
        tryNotifyCommentGoods(goods, userId, text);
        tryNotifyMention(userId, mentionUserIds, "GOODS", goodsId, text);
        // 楼中楼回复：同时通知被回复的那个评论作者（避免与内容作者重复、避免自通知）
        if (comment.getParentId() > 0 && parentComment != null) {
            tryNotifyReplyComment("GOODS", goodsId, parentComment.getUserId(), goods.getUserId(), userId, text);
        }
        return comment.getId();
    }

    /** 通用 @ 通知（Goods 场景） */
    private void tryNotifyMention(Long actorId, List<Long> mentionIds, String targetType, Long targetId, String content) {
        if (mentionIds == null || mentionIds.isEmpty()) return;
        List<Long> toValidate = mentionIds.stream().filter(id -> !id.equals(actorId)).toList();
        if (toValidate.isEmpty()) return;
        try {
            R<List<Long>> res = followClient.check(actorId, toValidate);
            if (res == null || res.getData() == null || res.getData().isEmpty()) return;
            for (Long mentionedId : res.getData()) {
                NotifyReq req = new NotifyReq();
                req.setType("MENTION");
                req.setReceiverId(mentionedId);
                req.setActorId(actorId);
                req.setTargetType(targetType);
                req.setTargetId(targetId);
                req.setContentSnapshot(content);
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try { notificationClient.send(req); }
                    catch (Exception e) { log.warn("[notify] mentionGoods failed receiverId={}: {}", mentionedId, e.getMessage()); }
                });
            }
        } catch (Exception e) {
            log.warn("[notify] mentionGoods check failed actorId={}: {}", actorId, e.getMessage());
        }
    }

    private void tryNotifyCommentGoods(Goods goods, Long actorId, String content) {
        Long receiverId = goods.getUserId();
        if (receiverId == null || receiverId.equals(actorId)) return;
        NotifyReq req = new NotifyReq();
        req.setType("COMMENT_GOODS");
        req.setReceiverId(receiverId);
        req.setActorId(actorId);
        req.setTargetType("GOODS");
        req.setTargetId(goods.getId());
        req.setContentSnapshot(content);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { notificationClient.send(req); }
            catch (Exception e) { log.warn("[notify] commentGoods failed goodsId={}: {}", goods.getId(), e.getMessage()); }
        });
    }

    /** 楼中楼回复通知：通知被回复的评论作者（已排除自通知、已排除与内容作者重复） */
    private void tryNotifyReplyComment(String targetType, Long targetId, Long parentAuthorId, Long contentAuthorId, Long actorId, String content) {
        if (parentAuthorId == null || parentAuthorId.equals(actorId)) return;
        if (parentAuthorId.equals(contentAuthorId)) return; // 内容作者已由 tryNotifyCommentXxx 覆盖
        String type = switch (targetType) {
            case "GOODS" -> "COMMENT_GOODS";
            case "POST" -> "COMMENT_POST";
            case "WALL" -> "COMMENT_WALL";
            default -> "COMMENT";
        };
        NotifyReq req = new NotifyReq();
        req.setType(type);
        req.setReceiverId(parentAuthorId);
        req.setActorId(actorId);
        req.setTargetType(targetType);
        req.setTargetId(targetId);
        req.setContentSnapshot(content);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { notificationClient.send(req); }
            catch (Exception e) { log.warn("[notify] replyComment failed targetType={} targetId={} replyTo={}: {}", targetType, targetId, parentAuthorId, e.getMessage()); }
        });
    }

    /** 删除自己的评论（删顶层评论时级联删除其下回复） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long goodsId, Long commentId) {
        Long userId = UserContext.requireUserId();
        GoodsComment comment = commentMapper.selectById(commentId);
        if (comment == null || !goodsId.equals(comment.getGoodsId())) {
            throw BizException.notFound("评论不存在或已删除");
        }
        if (!userId.equals(comment.getUserId())) {
            throw BizException.forbidden("只能删除自己的评论");
        }
        int removed = 0;
        if (comment.getParentId() == null || comment.getParentId() == 0) {
            List<GoodsComment> replies = commentMapper.selectList(Wrappers.<GoodsComment>lambdaQuery()
                    .eq(GoodsComment::getParentId, commentId));
            if (!replies.isEmpty()) {
                commentMapper.deleteByIds(replies.stream().map(GoodsComment::getId).toList());
                removed = replies.size();
            }
        }
        commentMapper.deleteById(commentId);
        goodsMapper.incrComment(goodsId, -(1 + removed));
    }

    private List<String> splitImages(String images) {
        return StringUtils.hasText(images)
                ? Arrays.stream(images.split(",")).filter(StringUtils::hasText).toList()
                : List.of();
    }

    private String joinImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream().filter(StringUtils::hasText).limit(3).collect(Collectors.joining(","));
    }

    public List<Goods> myFavorites(Long userId) {
        List<Long> ids = likeMapper.selectList(Wrappers.<Like>lambdaQuery()
                        .eq(Like::getTargetType, TARGET).eq(Like::getUserId, userId)
                        .orderByDesc(Like::getCreatedAt))
                .stream().map(Like::getTargetId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Goods> list = goodsMapper.selectBatchIds(ids);
        list.forEach(this::fillExtra);
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(Long goodsId, GoodsOrderReq req) {
        Long buyerId = UserContext.requireVerifiedUserId();
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw BizException.notFound("商品不存在");
        }
        if (goods.getUserId().equals(buyerId)) {
            throw new BizException("不能购买自己发布的商品");
        }
        if (goods.getStatus() == null || goods.getStatus() != 1) {
            throw new BizException("商品已下架或已售出");
        }
        long exists = orderMapper.selectCount(Wrappers.<GoodsOrder>lambdaQuery()
                .eq(GoodsOrder::getGoodsId, goodsId)
                .eq(GoodsOrder::getBuyerId, buyerId)
                .in(GoodsOrder::getStatus, 0, 1));
        if (exists > 0) {
            throw new BizException("你已提交过购买意向，请等待卖家联系");
        }
        GoodsOrder order = new GoodsOrder();
        order.setGoodsId(goodsId);
        order.setBuyerId(buyerId);
        order.setSellerId(goods.getUserId());
        order.setBuyerName(nicknameOf(buyerId));
        order.setMessage(req.getMessage());
        order.setContact(req.getContact());
        order.setStatus(0);
        orderMapper.insert(order);
        return order.getId();
    }

    public List<GoodsOrder> myOrders(Long userId, String role) {
        List<GoodsOrder> list = orderMapper.selectList(Wrappers.<GoodsOrder>lambdaQuery()
                .eq("seller".equalsIgnoreCase(role), GoodsOrder::getSellerId, userId)
                .eq(!"seller".equalsIgnoreCase(role), GoodsOrder::getBuyerId, userId)
                .orderByDesc(GoodsOrder::getCreatedAt));
        if (list.isEmpty()) {
            return list;
        }
        Map<Long, String> titles = goodsMapper
                .selectBatchIds(list.stream().map(GoodsOrder::getGoodsId).distinct().toList())
                .stream().collect(Collectors.toMap(Goods::getId, Goods::getTitle));
        list.forEach(o -> o.setGoodsTitle(titles.get(o.getGoodsId())));
        return list;
    }

    // ---------------- 内部方法 ----------------

    private void apply(Goods goods, GoodsReq req) {
        goods.setTitle(req.getTitle().trim());
        goods.setDescription(req.getDescription());
        goods.setPrice(req.getPrice());
        goods.setOriginalPrice(req.getOriginalPrice());
        goods.setCategoryId(req.getCategoryId() == null ? 6 : req.getCategoryId());
        goods.setConditionLevel(req.getConditionLevel());
        goods.setPurchaseTime(trimToNull(req.getPurchaseTime()));
        goods.setUsageFrequency(trimToNull(req.getUsageFrequency()));
        goods.setSource(trimToNull(req.getSource()));
        goods.setWarranty(trimToNull(req.getWarranty()));
        goods.setSupportTrial(req.getSupportTrial() == null ? 1 : req.getSupportTrial());
        goods.setTradeType(req.getTradeType() == null ? 1 : req.getTradeType());
        goods.setLocation(req.getLocation());
        if (req.getImages() != null) {
            goods.setImages(req.getImages().stream()
                    .filter(StringUtils::hasText).limit(9).collect(Collectors.joining(",")));
        }
    }

    private String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /** 卖家在售/累计发布数，供详情页卖家卡展示（读时统计，不落库） */
    private void fillSellerStats(Goods goods) {
        if (goods.getUserId() == null) {
            return;
        }
        Long sale = goodsMapper.selectCount(Wrappers.<Goods>lambdaQuery()
                .eq(Goods::getUserId, goods.getUserId()).eq(Goods::getStatus, 1));
        Long total = goodsMapper.selectCount(Wrappers.<Goods>lambdaQuery()
                .eq(Goods::getUserId, goods.getUserId()));
        goods.setSellerSaleCount(sale == null ? 0 : sale.intValue());
        goods.setSellerTotalCount(total == null ? 0 : total.intValue());
    }

    private Goods requireOwner(Long id, Long userId) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) {
            throw BizException.notFound("商品不存在");
        }
        if (!goods.getUserId().equals(userId)) {
            throw BizException.forbidden("只能操作自己发布的商品");
        }
        return goods;
    }

    private void fillSeller(Goods goods, Long userId) {
        try {
            R<UserBrief> res = userClient.getBrief(userId);
            if (res != null && res.getData() != null) {
                goods.setSellerName(res.getData().getNickname());
                goods.setSellerAvatar(res.getData().getAvatar());
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败 userId={}, {}", userId, e.getMessage());
        }
        if (!StringUtils.hasText(goods.getSellerName())) {
            goods.setSellerName("校园用户");
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

    private boolean hasLiked(Long goodsId, Long userId) {
        return likeMapper.selectCount(Wrappers.<Like>lambdaQuery()
                .eq(Like::getTargetType, TARGET)
                .eq(Like::getTargetId, goodsId)
                .eq(Like::getUserId, userId)) > 0;
    }

    /**
     * 用 t_user 的最新昵称/头像覆盖商品里的卖家快照。
     *
     * seller_name / seller_avatar 是发布时写下的快照，卖家改完资料后列表仍显示旧名字，
     * 与「我的」页对不上。一次 IN 查询补齐，取不到就沿用快照，异常不影响列表。
     */
    private void refreshSellers(List<Goods> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> userIds = list.stream()
                .map(Goods::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        try {
            Map<Long, UserBrief> briefs = userSnapshotMapper.selectBriefByIds(userIds).stream()
                    .collect(Collectors.toMap(UserBrief::getId, u -> u, (a, b) -> a));
            for (Goods goods : list) {
                UserBrief brief = briefs.get(goods.getUserId());
                if (brief == null) {
                    continue;
                }
                if (StringUtils.hasText(brief.getNickname())) {
                    goods.setSellerName(brief.getNickname());
                }
                if (StringUtils.hasText(brief.getAvatar())) {
                    goods.setSellerAvatar(brief.getAvatar());
                }
            }
        } catch (Exception e) {
            log.warn("刷新卖家资料失败，沿用快照: {}", e.getMessage());
        }
    }

    private void fillExtra(Goods goods) {
        if (StringUtils.hasText(goods.getImages())) {
            goods.setImageList(Arrays.stream(goods.getImages().split(","))
                    .filter(StringUtils::hasText).toList());
        } else {
            goods.setImageList(List.of());
        }
        goods.setCategoryName(CATEGORIES.stream()
                .filter(c -> c.id().equals(goods.getCategoryId()))
                .map(CategoryVO::name).findFirst().orElse("其他"));
    }
}
