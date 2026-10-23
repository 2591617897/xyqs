package com.campus.goods.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.api.R;
import com.campus.common.client.UserClient;
import com.campus.common.client.dto.UserBrief;
import com.campus.common.exception.BizException;
import com.campus.common.security.UserContext;
import com.campus.common.social.Like;
import com.campus.common.social.mapper.LikeMapper;
import com.campus.goods.dto.GoodsOrderReq;
import com.campus.goods.dto.GoodsReq;
import com.campus.goods.entity.Goods;
import com.campus.goods.entity.GoodsOrder;
import com.campus.goods.mapper.GoodsMapper;
import com.campus.goods.mapper.GoodsOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    private final LikeMapper likeMapper;
    private final UserClient userClient;

    public List<CategoryVO> categories() {
        return CATEGORIES;
    }

    public R.Page<Goods> page(String keyword, Integer categoryId, String sort,
                              long page, long size, Long schoolId) {
        LambdaQueryWrapper<Goods> w = Wrappers.<Goods>lambdaQuery().eq(Goods::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword));
        }
        if (categoryId != null && categoryId > 0) {
            w.eq(Goods::getCategoryId, categoryId);
        }
        if (schoolId != null && schoolId > 0) {
            w.eq(Goods::getSchoolId, schoolId);
        }
        switch (sort == null ? "latest" : sort) {
            case "price_asc" -> w.orderByAsc(Goods::getPrice);
            case "price_desc" -> w.orderByDesc(Goods::getPrice);
            case "hot" -> w.orderByDesc(Goods::getViewCount);
            default -> w.orderByDesc(Goods::getCreatedAt);
        }
        Page<Goods> p = goodsMapper.selectPage(new Page<>(page, size), w);
        p.getRecords().forEach(this::fillExtra);
        return R.Page.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
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
        goods.setStatus(1);
        goods.setViewCount(0);
        goods.setLikeCount(0);
        goodsMapper.insert(goods);
        return goods.getId();
    }

    public void update(Long id, GoodsReq req) {
        Goods goods = requireOwner(id, UserContext.requireUserId());
        if (goods.getStatus() != null && goods.getStatus() == 2) {
            throw new BizException("已售出的商品不能修改");
        }
        apply(goods, req);
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
        return true;
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
