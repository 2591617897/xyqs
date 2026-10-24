/**
 * 学校列表「按定位城市推荐」的共用逻辑（认证页 + 新用户引导弹层共用）。
 *
 * 定位方案选 IP 定位而不是 wx.getLocation：
 *   - 小程序 getLocation 需要用户授权 + app.json 声明 requiredPrivateInfos，
 *     为一个「选学校」的辅助排序去弹授权框，转化率会掉；
 *   - IP 定位由后端读请求来源 IP 调腾讯位置服务完成，用户无感，精度到城市级 ——
 *     而学校本身只有省/市字段，「离我最近」实际上就等于「同城」。
 *
 * 所以本模块只做两件事：拿 IP 城市、把同城学校提到最前并加分组标题。
 */
var http = require('./request')

/** 推荐区最多展示多少所（其余仍留在「全部学校」里，不会丢） */
var NEARBY_LIMIT = 8

/** 进程内缓存：一次运行只问一次，失败也不反复重试 */
var cachedCity = null
var pending = null

/**
 * 行政区别名归一：「贵阳市」和库里的「贵阳」、「贵州省」和「贵州」要能对上。
 * 只削尾部一个行政区划后缀，不动中间字（如「黔南布依族苗族自治州」→「黔南布依族苗族」）。
 */
function normRegion(s) {
  return String(s || '')
    .replace(/(特别行政区|自治区|自治州|地区|省|市|盟|县)$/, '')
    .trim()
}

/** 两个地区名是否指同一个地方：归一后相等，或一方包含另一方（「黔南」vs「黔南布依族苗族」） */
function sameRegion(a, b) {
  var x = normRegion(a)
  var y = normRegion(b)
  if (!x || !y) return false
  if (x === y) return true
  return x.indexOf(y) >= 0 || y.indexOf(x) >= 0
}

/**
 * 取 IP 定位城市（失败返回 ''，前端静默降级为不分组）。
 * @returns {Promise<String>} 如「贵阳市」
 */
function ipCity() {
  if (cachedCity !== null) return Promise.resolve(cachedCity)
  if (pending) return pending
  pending = http.get('/auth/geo/ip-city', null, { silent: true }).then(function (geo) {
    cachedCity = (geo && geo.available && geo.city) ? geo.city : ''
    pending = null
    return cachedCity
  }).catch(function () {
    cachedCity = ''
    pending = null
    return ''
  })
  return pending
}

/**
 * 生成「带分组标题的扁平数组」，页面用一个 wx:for 就能渲染（WXML 没有局部模板，避免重复写两遍卡片）。
 * 传入 city 为空时返回不分组的原始列表（搜索场景直接传 ''）。
 *
 * @param {Array} schools 学校数组
 * @param {String} city 定位城市
 * @param {Number} [limit] 推荐区条数上限
 * @returns {Array<{_key:String, group:String, id, name, province, city}>}
 */
function buildList(schools, city, limit) {
  var all = (schools || []).map(function (s) {
    // 调用方已给过 _key（如引导组件用 String(id) 做选中态比较）就保留，避免破坏其比较口径
    return Object.assign({}, s, { _key: s._key || ('s' + s.id) })
  })
  if (!city) return all

  var near = all.filter(function (s) {
    // city 缺失的历史数据退回按省匹配，宁可多推荐也不要空着
    return s.city ? sameRegion(s.city, city) : sameRegion(s.province, city)
  })
  if (!near.length) {
    // 定位成功但没有同城学校：也给一行说明，别让用户以为定位没生效
    return [{ _key: '_g_near', tone: 'all', group: '📍 已定位到 ' + city + ' · 暂无同城学校' }].concat(all)
  }

  var max = limit || NEARBY_LIMIT
  var head = near.slice(0, max)
  var shown = {}
  head.forEach(function (s) { shown[s._key] = 1 })
  var rest = all.filter(function (s) { return !shown[s._key] })

  var out = [{ _key: '_g_near', tone: 'near', group: '📍 已定位到 ' + city + ' · 同城 ' + near.length + ' 所' }]
  head.forEach(function (s) { out.push(s) })
  if (rest.length) {
    out.push({ _key: '_g_all', tone: 'all', group: '全部学校' })
    rest.forEach(function (s) { out.push(s) })
  }
  return out
}

/** 关键词过滤：学校名或城市命中 */
function search(schools, keyword) {
  var kw = String(keyword || '').trim()
  if (!kw) return schools || []
  return (schools || []).filter(function (s) {
    return (s.name && s.name.indexOf(kw) >= 0) || (s.city && s.city.indexOf(kw) >= 0)
  })
}

module.exports = {
  NEARBY_LIMIT: NEARBY_LIMIT,
  normRegion: normRegion,
  sameRegion: sameRegion,
  ipCity: ipCity,
  buildList: buildList,
  search: search
}
