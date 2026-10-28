package com.campus.goods.controller;

import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.goods.entity.Demand;
import com.campus.goods.mapper.DemandMapper;
import com.campus.goods.mapper.DemandReplyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.goods.entity.DemandReply;

import com.campus.goods.service.DemandService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台管理专用内部接口（campus-admin → campus-goods）：需求广场处置。
 *
 * <p>后台只能把 1进行中 改成 0下架（强制下架），或把 0下架 恢复到 1进行中。
 * 不碰 2已完成 —— 那是发布者自己关闭的需求（已找到），重新拉回进行中会让
 * 已经对接过的人再来一遍。
 */
@Slf4j
@RestController
@RequestMapping("/internal/admin/demands")
@RequiredArgsConstructor
public class InternalAdminDemandController {

    private final DemandMapper demandMapper;
    private final DemandReplyMapper replyMapper;
    private final DemandService demandService;

    @PostMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("status");
        Integer status = raw == null ? null : Integer.valueOf(String.valueOf(raw));
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of("status 非法，后台只能下架(0)或恢复进行中(1)");
        }
        Demand demand = demandMapper.selectById(id);
        if (demand == null) {
            throw BizException.notFound("需求不存在");
        }
        if (Integer.valueOf(Demand.STATUS_DONE).equals(demand.getStatus())) {
            throw BizException.of("需求已完成，不可改回进行中");
        }
        Demand patch = new Demand();
        patch.setId(id);
        patch.setStatus(status);
        demandMapper.updateById(patch);
        log.info("[admin] 需求 {} -> status={}，原因：{}", id, status, body.get("reason"));
        return R.ok();
    }

    /**
     * 需求分类字典。
     *
     * <p>后台要显示「数码 / 代取代办」这种中文名，但分类白名单在
     * {@code DemandService.DEMAND_CATEGORIES}（唯一数据源）——
     * 后台不能自己抄一份，否则加分类时后台不显示。所以由本服务下发。
     */
    @GetMapping("/categories")
    public R<List<Map<String, Object>>> categories() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DemandService.DemandCategoryVO c : demandService.categories(null)) {
            Map<String, Object> m = new LinkedHashMap<>(4);
            m.put("type", c.type());
            m.put("id", c.id());
            m.put("name", c.name());
            m.put("icon", c.icon());
            list.add(m);
        }
        return R.ok(list);
    }

    /** 后台删除需求：与前台删除一样，级联清掉回应，避免留下孤儿行 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        demandMapper.deleteById(id);
        replyMapper.delete(Wrappers.<DemandReply>lambdaQuery().eq(DemandReply::getDemandId, id));
        log.info("[admin] 需求 {} 已删除", id);
        return R.ok();
    }
}
