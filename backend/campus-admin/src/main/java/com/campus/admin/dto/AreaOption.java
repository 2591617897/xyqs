package com.campus.admin.dto;

import java.util.List;

/**
 * 省 / 市下拉选项（组织节点表单用）。
 *
 * <p>数据来自 t_school 的 province / city 去重结果，<b>不是</b>一张独立的行政区域表。
 * 原因：组织节点的 province/city 要拿去和 t_school 做等值匹配才算辖区
 * （见 AdminRegionMapper#selectSchoolIdsByRegions），自己维护一份行政区划只会
 * 出现「表单里是贵州省、学校表里是贵州」这种永远匹配不上的脏数据。
 */
public record AreaOption(String province, List<String> cities) {
}
