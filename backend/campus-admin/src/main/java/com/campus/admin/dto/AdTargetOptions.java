package com.campus.admin.dto;

import java.util.List;

/**
 * 广告位「投放范围」的候选值：城市多选 + 校区多选。
 *
 * <p>两个列表都取自 t_school 的真实数据，不是自己维护的行政区划表——
 * 只有这样「能选的值」才天然等于「C 端会拿来匹配的值」。
 * 手输时代最常见的故障就是运营填了「贵阳市」而学校表存的是「贵阳」，
 * 于是这条广告位对任何人都不命中。
 *
 * @param cities  去重后的城市名（不带「市」字，与 t_school.city 逐字一致）
 * @param schools 全部校区（id/name/province/city），前端做搜索过滤
 */
public record AdTargetOptions(List<String> cities, List<SchoolOption> schools) {
}
