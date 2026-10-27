/**
 * 内容可见范围（闲置 / 帖子发布页共用）
 *
 * - school：仅本校可见（默认，也是安全值）
 * - city  ：同城所有高校可见
 *
 * ⚠️ 别和浏览范围（utils/scope.js 的 school/city）搞混：
 *    scope 是「我在以什么身份逛」，visibility 是「这条内容允许被谁看到」。
 *    两者取值长得一样，但语义完全不同，取值来源也不同（一个来自本地存储，一个来自内容字段）。
 *
 * 服务端规则一致（GoodsService#page / PostService#pageInternal）：
 *   浏览本校：本校内容无论可见范围如何都可见
 *   浏览本市：他校内容只有 visibility=city 才进流，本校内容仍无条件可见
 */
const SCHOOL = 'school'
const CITY = 'city'

/** 归一化：只认 city，其它（含 null / 空串 / 大小写变体）一律按 school */
function normalize(v) {
  return v === CITY ? CITY : SCHOOL
}

function isCity(v) {
  return v === CITY
}

/** 一句话文案，列表/详情展示用 */
function text(v) {
  return isCity(normalize(v)) ? '同市可见' : '仅本校'
}

/**
 * 发布页的两个选项。
 * 学校信息缺失时退化成通用文案，避免出现「undefined 的同学能看到」这种空槽。
 */
function options(schoolName, schoolCity) {
  const school = schoolName || '本校'
  const city = schoolCity || '同市'
  return [
    {
      value: SCHOOL,
      icon: '🏫',
      title: '仅本校',
      sub: '只有' + school + '的同学能看到'
    },
    {
      value: CITY,
      icon: '🌆',
      title: '同市可见',
      sub: city + '所有高校的同学都能看到'
    }
  ]
}

module.exports = {
  SCHOOL: SCHOOL,
  CITY: CITY,
  normalize: normalize,
  isCity: isCity,
  text: text,
  options: options
}
