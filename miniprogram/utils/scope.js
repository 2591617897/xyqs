/**
 * 浏览范围（信息流圈子）：本校 / 本市。
 *
 * - scope: 'school' 本校（默认，只看自己学校的内容）
 * - scope: 'city'   本市（同一城市所有高校的内容）
 * - city:  本市模式下的城市名；默认学校所在市，定位到异地时可为定位城市
 *
 * 位置信息 API：取当前城市走「IP 归属地」（后端按请求来源 IP 解析，见
 * /auth/geo/ip-city）。**不使用 wx.getLocation** —— 它自 2022-07-14 起属隐私接口，
 * 必须在 app.json 声明 requiredPrivateInfos 并在 mp 后台申请开通，否则真机直接 fail；
 * 而本场景只需要城市粒度，IP 归属地足够且用户完全无感。
 * 失败时返回 null，前端回退到学校所在市。
 */
const http = require('./request')
const { mp } = require('./util')

const KEY = 'feedScope'

function read() {
  const s = mp.getStorageSync(KEY)
  if (s && (s.scope === 'school' || s.scope === 'city')) {
    return { scope: s.scope, city: s.city || '' }
  }
  return { scope: 'school', city: '' }
}

function write(next) {
  const v = { scope: next && next.scope === 'city' ? 'city' : 'school', city: (next && next.city) || '' }
  mp.setStorageSync(KEY, v)
  return v
}

/**
 * 拼进列表请求（/posts、/wall/posts）的查询参数。
 * 本校模式返回空对象，沿用后端默认的本校过滤。
 */
function postParams() {
  const s = read()
  if (s.scope !== 'city') return {}
  const q = { scope: 'city' }
  if (s.city) q.city = s.city
  return q
}

/**
 * 拼进闲置列表请求（/goods）的查询参数，逻辑与帖子一致：
 * 本市模式下让闲置也按同城高校混排，避免切换范围后闲置仍停留在本校。
 */
function goodsParams() {
  return postParams()
}

/** 胶囊文案：本校显示学校名，本市显示「城市 · 全市」 */
function label(schoolName, schoolCity) {
  const s = read()
  if (s.scope === 'city') {
    return (s.city || schoolCity || '本市') + ' · 全市'
  }
  return schoolName || '选择学校'
}

/**
 * 位置信息 API：解析当前城市（IP 归属地，无需用户授权）。
 * 任何一步失败都返回 null（静默降级，不打扰用户）。
 * @returns {Promise<{city: string, district: string}|null>}
 */
function resolveGeoCity() {
  return http.get('/auth/geo/ip-city', null, { silent: true }).then(function (geo) {
    if (!geo || !geo.available || !geo.city) return null
    return { city: geo.city, district: geo.district || '' }
  }).catch(function () {
    return null
  })
}

module.exports = {
  read: read,
  write: write,
  postParams: postParams,
  goodsParams: goodsParams,
  label: label,
  resolveGeoCity: resolveGeoCity
}
