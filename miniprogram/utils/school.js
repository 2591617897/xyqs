/**
 * 学校列表「按定位城市推荐」的共用逻辑（认证页 + 新用户引导弹层共用）。
 *
 * 定位方案选 IP 定位而不是 wx.getLocation：
 *   - 小程序 getLocation 需要用户授权 + app.json 声明 requiredPrivateInfos，
 *     为一个「选学校」的辅助排序去弹授权框，转化率会掉；
 *   - IP 定位由后端读请求来源 IP 调腾讯位置服务完成，用户无感，精度到城市级 ——
 *     而学校本身只有省/市字段，「离我最近」实际上就等于「同城」。
 *
 * 所以本模块只做三件事：拿 IP 城市、把同城学校提到最前、拿不到城市时按城市分组。
 *
 * ⚠️ 两个坑（都踩过）：
 *   1) 腾讯 IP 定位返回的是「黔南布依族苗族自治州」，而 t_school.city 存的是州府「都匀」，
 *      直接按字符串比永远对不上 → 贵州三个自治州必须走 PREFECTURE_SEAT 映射；
 *   2) 定位接口会因「key 每日调用量上限」（status=121）整体不可用，
 *      此时**不能**退化成一条 80 多条铺到底的长列表 —— 按城市分组，用户至少能自己找。
 */
var http = require('./request')
var kwUtil = require('./kw')
var mp = require('./util').mp

/** 推荐区最多展示多少所（其余仍留在「全部学校」里，不会丢） */
var NEARBY_LIMIT = 8

/**
 * 自治州 → 州府所在地。
 * IP 定位给的是「XX自治州」，学校表存的是州府市名，贵州三个州全部命中这张表。
 * 新增省份的自治州往这里加一行即可（键用州名里的唯一短词，
 * 注意「黔东南」「黔西南」不含子串「黔南」，短词不会误命中）。
 */
var PREFECTURE_SEAT = [
  ['黔南', '都匀'],   // 黔南布依族苗族自治州
  ['黔东南', '凯里'], // 黔东南苗族侗族自治州
  ['黔西南', '兴义']  // 黔西南布依族苗族自治州
]

/** 进程内缓存：一次运行只问一次；失败按 FAIL_BACKOFF 退避，避免反复打已超限的 key */
var cachedCity = null
var pending = null
var failedAt = 0
var FAIL_BACKOFF_MS = 60 * 1000

/** 定位结果的本地缓存：第三方 key 配额抖动时，别让「同城推荐」整体消失 */
var STORAGE_KEY = 'geoCityCache'
var STORAGE_TTL_MS = 24 * 60 * 60 * 1000
var STORAGE_MAX_MS = 7 * 24 * 60 * 60 * 1000

/**
 * 行政区别名归一：「贵阳市」和库里的「贵阳」、「贵州省」和「贵州」要能对上。
 * 只削尾部一个行政区划后缀，不动中间字（如「黔南布依族苗族自治州」→「黔南布依族苗族」）。
 */
function normRegion(s) {
  return String(s || '')
    .replace(/(特别行政区|自治区|自治州|地区|省|市|盟|县)$/, '')
    .trim()
}

/** 自治州 → 州府（不是自治州返回空串） */
function seatOf(name) {
  var n = String(name || '')
  for (var i = 0; i < PREFECTURE_SEAT.length; i++) {
    if (n.indexOf(PREFECTURE_SEAT[i][0]) >= 0) return PREFECTURE_SEAT[i][1]
  }
  return ''
}

/**
 * 两个地区名是否指同一个地方：
 *   - 归一后相等（贵阳 / 贵阳市 / 贵州省）
 *   - 一方包含另一方（黔南 vs 黔南布依族苗族）
 *   - 州 ↔ 州府（黔南布依族苗族自治州 vs 都匀）
 */
function sameRegion(a, b) {
  var x = normRegion(a)
  var y = normRegion(b)
  if (!x || !y) return false
  if (x === y) return true
  if (x.indexOf(y) >= 0 || y.indexOf(x) >= 0) return true
  // 州 ↔ 州府：两边各自折算成「州府名」后再比一次
  var nx = normRegion(seatOf(a) || x)
  var ny = normRegion(seatOf(b) || y)
  if (!nx || !ny) return false
  if (nx === ny) return true
  return nx.indexOf(ny) >= 0 || ny.indexOf(nx) >= 0
}

/** 读本地缓存的定位城市（彻底过期返回 null） */
function readCache() {
  var v = mp.getStorageSync(STORAGE_KEY)
  if (!v || !v.city || !v.at) return null
  // 超过 STORAGE_MAX_MS 视为彻底过期（换了城市/换了网太久，不如不猜）
  if (Date.now() - v.at > STORAGE_MAX_MS) return null
  return v
}

/**
 * 取 IP 定位城市（失败返回 ''，前端降级为「按城市分组」）。
 *
 * 三层兜底，顺序不能反：
 *   1. 进程内缓存（本次运行已成功拿到）
 *   2. 本地缓存（24h 内直接复用，省腾讯配额；超过 24h 仍可作为失败兜底）
 *   3. 请求接口；失败后 60s 内不再重试（key 配额耗尽时每秒重试没有任何意义）
 * @returns {Promise<String>} 如「黔南布依族苗族自治州」
 */
function ipCity() {
  if (cachedCity !== null) return Promise.resolve(cachedCity)
  if (pending) return pending

  var saved = readCache()
  if (saved && Date.now() - saved.at < STORAGE_TTL_MS) {
    cachedCity = saved.city
    return Promise.resolve(cachedCity)
  }
  if (failedAt && Date.now() - failedAt < FAIL_BACKOFF_MS) {
    return Promise.resolve(saved ? saved.city : '')
  }

  pending = http.get('/auth/geo/ip-city', null, { silent: true }).then(function (geo) {
    pending = null
    var city = (geo && geo.available && geo.city) ? geo.city : ''
    if (city) {
      cachedCity = city
      failedAt = 0
      mp.setStorageSync(STORAGE_KEY, { city: city, at: Date.now() })
      return city
    }
    // 定位失败：还能用本地缓存就先顶着，总比「同城推荐」整块消失好
    failedAt = Date.now()
    cachedCity = saved ? saved.city : ''
    return cachedCity
  }).catch(function () {
    pending = null
    failedAt = Date.now()
    cachedCity = saved ? saved.city : ''
    return cachedCity
  })
  return pending
}

/** 统一给每条学校补 _key（后端雪花 ID 是字符串、mock 是数字，必须归一才能做 key/选中态） */
function normalize(schools) {
  return (schools || []).map(function (s) {
    // 调用方已给过 _key（如引导组件用 String(id) 做选中态比较）就保留，避免破坏其比较口径
    return Object.assign({}, s, { _key: s._key || ('s' + s.id) })
  })
}

/** 平铺列表（搜索场景用：不插分组标题，用户已经在缩小范围了） */
function flatList(schools) {
  return normalize(schools)
}

/**
 * 按城市分组：组间先按「省份学校数」降序、再按「城市学校数」降序，组内保持传入顺序。
 *
 * 为什么先看省份：库里 81 所里贵州占绝大多数，纯按城市数量排会把「北京 4 / 成都 4」
 * 混到「贵阳 24 / 铜仁 5」中间，用户要滑很久才能看到自己的城市。
 * 先按省份聚合，本省城市自然连成一片排在最前。
 *
 * 定位失败时用它兜底 —— 一条 80 多所铺到底的列表在手机上是没法用的。
 */
function groupByCity(list, leadGroup) {
  var groups = {}
  var order = []
  var provCount = {}
  list.forEach(function (s) {
    var c = s.city || s.province || '其他'
    var p = s.province || '其他'
    provCount[p] = (provCount[p] || 0) + 1
    if (!groups[c]) {
      groups[c] = { items: [], province: p }
      order.push(c)
    }
    groups[c].items.push(s)
  })
  order.sort(function (a, b) {
    var pa = provCount[groups[a].province] || 0
    var pb = provCount[groups[b].province] || 0
    if (pa !== pb) return pb - pa
    return groups[b].items.length - groups[a].items.length
  })
  var out = []
  if (leadGroup) out.push({ _key: '_g_hint', tone: 'all', group: leadGroup })
  order.forEach(function (c) {
    var g = groups[c]
    out.push({ _key: '_g_c_' + c, tone: 'all', group: c + ' · ' + g.items.length + ' 所' })
    g.items.forEach(function (s) {
      out.push(s)
    })
  })
  return out
}

/**
 * 生成「带分组标题的扁平数组」，页面用一个 wx:for 就能渲染（WXML 没有局部模板，避免重复写两遍卡片）。
 *
 * - 定位到城市：同城学校置顶（最多 NEARBY_LIMIT 所），其余进「全部学校」
 * - 定位到但没同城学校：先说明，再按城市分组
 * - 完全没定位：直接按城市分组（**不要平铺**）
 *
 * @param {Array} schools 学校数组
 * @param {String} city 定位城市
 * @param {Number} [limit] 推荐区条数上限
 * @returns {Array<{_key:String, group:String, tone:String, id, name, province, city}>}
 */
function buildList(schools, city, limit) {
  var all = normalize(schools)
  if (!city) return groupByCity(all)

  var near = all.filter(function (s) {
    // city 缺失的历史数据退回按省匹配，宁可多推荐也不要空着
    return s.city ? sameRegion(s.city, city) : sameRegion(s.province, city)
  })
  if (!near.length) {
    // 定位成功但没有同城学校：说明白，再给分组列表让用户自己找
    return groupByCity(all, '📍 已定位到 ' + city + ' · 暂无同城学校')
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

/**
 * 关键词过滤：学校名或城市命中。
 * 支持多词：「贵州 师范」= 名字里同时含这两个词 —— 和 /schools 接口的搜索口径一致。
 */
function search(schools, keyword) {
  return kwUtil.filterList(schools || [], ['name', 'city'], keyword)
}

module.exports = {
  NEARBY_LIMIT: NEARBY_LIMIT,
  normRegion: normRegion,
  seatOf: seatOf,
  sameRegion: sameRegion,
  ipCity: ipCity,
  flatList: flatList,
  buildList: buildList,
  search: search
}
