// 微信小程序为主端；保留 QQ 小程序兜底（同一套代码两端都能跑）
const IS_QQ = typeof wx === 'undefined' && typeof qq !== 'undefined'
const mp = IS_QQ ? qq : wx

/** 宿主平台标识：登录时随 code 一起发给后端，决定用哪套 appid 去换 openid */
const PLATFORM = IS_QQ ? 'qq' : 'wx'

/** 平台展示名，用于文案 */
const PLATFORM_NAME = IS_QQ ? 'QQ' : '微信'

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

function parseDate(value) {
  if (!value) return null
  if (value instanceof Date) return value
  const str = String(value).replace(/-/g, '/').replace('T', ' ').replace(/\.\d+$/, '')
  const d = new Date(str)
  return isNaN(d.getTime()) ? null : d
}

/** 刚刚 / 3 分钟前 / 昨天 18:20 / 10-21 */
function timeAgo(value) {
  const d = parseDate(value)
  if (!d) return ''
  const diff = Date.now() - d.getTime()
  const min = 60 * 1000
  if (diff < min) return '刚刚'
  if (diff < 60 * min) return Math.floor(diff / min) + ' 分钟前'
  if (diff < 24 * 60 * min) return Math.floor(diff / (60 * min)) + ' 小时前'
  if (diff < 48 * 60 * min) return '昨天 ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
  return pad(d.getMonth() + 1) + '-' + pad(d.getDate())
}

function priceText(v) {
  const n = Number(v || 0)
  return n % 1 === 0 ? String(n) : n.toFixed(2)
}

function toast(title, icon) {
  mp.showToast({ title: title, icon: icon || 'none' })
}

const CATEGORY_EMOJI = { 1: '📱', 2: '📚', 3: '🧺', 4: '👕', 5: '🏀', 6: '✨' }

function categoryEmoji(id) {
  return CATEGORY_EMOJI[id] || '✨'
}

module.exports = {
  mp,
  PLATFORM,
  PLATFORM_NAME,
  timeAgo,
  priceText,
  toast,
  parseDate,
  categoryEmoji
}
