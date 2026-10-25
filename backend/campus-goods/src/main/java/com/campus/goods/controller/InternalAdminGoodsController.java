package com.campus.goods.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.goods.entity.Goods;
import com.campus.goods.entity.GoodsComment;
import com.campus.goods.mapper.GoodsCommentMapper;
import com.campus.goods.mapper.GoodsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台管理专用内部接口（campus-admin → campus-goods）。
 *
 * <p>后台只能把 1在售 改成 0下架（强制下架），或把 0下架 恢复到 1在售。
 * 绝不碰 2已售出——那是买卖双方已经完成的交易事实，重新拉回在售会造成重复交易。
 */
@Slf4j
@RestController
@RequestMapping("/internal/admin/goods")
@RequiredArgsConstructor
public class InternalAdminGoodsController {

    private final GoodsMapper goodsMapper;
    private final GoodsCommentMapper goodsCommentMapper;

    @PostMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("status");
        Integer status = raw == null ? null : Integer.valueOf(String.valueOf(raw));
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 非法，后台只能下架(0)或恢复在售(1)");
        }
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) {
            throw BizException.notFound("商品不存在");
        }
        if (Integer.valueOf(2).equals(goods.getStatus())) {
            throw BizException.of("商品已售出，不可改回在售");
        }
        Goods patch = new Goods();
        patch.setId(id);
        patch.setStatus(status);
        goodsMapper.updateById(patch);
        log.info("[admin] 商品 {} -> status={}，原因：{}", id, status, body.get("reason"));
        return R.ok();
    }

    /** 删除单条商品评论并回写评论数（不连带删楼中楼回复，理由同帖子评论） */
    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable("id") Long id) {
        GoodsComment comment = goodsCommentMapper.selectById(id);
        if (comment == null) {
            return R.ok();
        }
        goodsCommentMapper.deleteById(id);
        goodsMapper.update(null, Wrappers.<Goods>lambdaUpdate()
                .eq(Goods::getId, comment.getGoodsId())
                .setSql("comment_count = greatest(comment_count - 1, 0)"));
        log.info("[admin] 商品评论 {} 已删除", id);
        return R.ok();
    }
}
