/**
 * 公告弹窗：拉取 + 频次控制 + 回执。
 *
 * 频次完全由后台「通知公告」的 popupMode 决定：
 *   0 不弹窗（只在首页公告条露出）
 *   1 每次进入（每次冷启动弹一次，切页面/返回首页不重复）
 *   2 每天一次
 *   3 确认收到后不再显示
 *
 * 设计取舍：**判定放在本地，服务端只做候选**。
 * 游客没有 user_id，服务端落不了回执，如果只靠服务端判定，游客会每次都被弹；
 * 而本地存储对游客和登录用户都生效，行为一致、离线也准。
 * 服务端对登录用户同样会判一次（t_notice_receipt），两层都过才弹——
 * 这是双保险，不是重复实现：本地保证「刚弹过不弹」，服务端保证「换设备也知道弹过」。
 */
const http = require('./request')
const { mp } = require('./util')

const STORE_KEY = 'noticePopupState'
/**
 * 本次冷启动已经弹过的公告 id。
 *
 * 用模块级变量而不是 storage：小程序冷启动会重新加载 JS，变量自然清空，
 * 正好等于「每次进入都弹」的语义；而页面间跳转不会重置，所以切 tab 不会再弹。
 */
const poppedThisLaunch = {}

/** 同一页面反复 onShow 时别把接口打爆（3 秒内只查一次） */
let lastFetchAt = 0
const FETCH_GAP = 3000

const TYPE_TEXT = { 1: '系统公告', 2: '运营活动', 3: '违规警示' }
const LEVEL_TEXT = { 1: '普通', 2: '重要', 3: '紧急' }

function todayKey() {
  const d = new Date()
  const m = d.getMonth() + 1
  const day = d.getDate()
  return d.getFullYear() + '-' + (m < 10 ? '0' + m : m) + '-' + (day < 10 ? '0' + day : day)
}

function readState() {
  const s = mp.getStorageSync(STORE_KEY)
  return (s && typeof s === 'object') ? s : {}
}

function writeState(state) {
  mp.setStorageSync(STORE_KEY, state)
}

/** 本地是否允许弹出 */
function allow(notice) {
  if (!notice || !notice.id) return false
  const mode = notice.popupMode || 0
  if (mode <= 0) return false
  if (mode === 1) return !poppedThisLaunch[notice.id]
  const s = readState()[notice.id] || {}
  if (mode === 2) return s.date !== todayKey()
  if (mode === 3) return !s.confirmed
  return false
}

/**
 * 记一次「已经弹给他看了」。
 *
 * mode=2 记当天日期：用户当场关掉也算今天弹过，否则一天能被弹十几次。
 * mode=3 这里不落 confirmed —— 只有点「我知道了」才算确认，点 × 只是关掉本次。
 */
function noteShown(notice) {
  if (!notice || !notice.id) return
  const mode = notice.popupMode || 0
  if (mode === 1) {
    poppedThisLaunch[notice.id] = true
    return
  }
  if (mode === 2) {
    const state = readState()
    const cur = state[notice.id] || {}
    cur.date = todayKey()
    state[notice.id] = cur
    writeState(state)
  }
}

/** 点「我知道了」：mode=3 永久静默，并同步服务端回执（游客会被服务端静默忽略） */
function noteConfirmed(notice) {
  if (!notice || !notice.id) return
  if ((notice.popupMode || 0) === 3) {
    const state = readState()
    const cur = state[notice.id] || {}
    cur.confirmed = true
    state[notice.id] = cur
    writeState(state)
  }
  http.post('/notices/' + notice.id + '/confirm', null, { silent: true })
    .catch(function () {})
}

/** 统一成渲染需要的形状：类型/级别文案、摘要、是否提醒「不再显示」 */
function decorate(n) {
  if (!n) return null
  const content = String(n.content || '')
  return {
    id: n.id,
    title: n.title || '',
    content: content,
    // 首页公告条只放一行，正文在 JS 里截好，避免长文把卡片撑高
    summary: content.replace(/\s+/g, ' ').slice(0, 46),
    popupMode: n.popupMode || 0,
    level: n.level || 1,
    typeText: TYPE_TEXT[n.type] || '公告',
    levelText: LEVEL_TEXT[n.level] || '普通',
    permanent: (n.popupMode || 0) === 3,
    publishText: String(n.publishAt || '').replace('T', ' ').slice(0, 16)
  }
}

/**
 * 拉取本次该弹的公告。
 *
 * 永远 resolve（不 reject）：公告是锦上添花，接口挂了首页照常显示，
 * 不能让一个弹窗把首屏打断。
 */
function fetchPopup() {
  const now = Date.now()
  if (now - lastFetchAt < FETCH_GAP) return Promise.resolve(null)
  lastFetchAt = now
  return http.get('/notices/popup', null, { silent: true }).then(function (n) {
    if (!n) return null
    return allow(n) ? decorate(n) : null
  }).catch(function () {
    return null
  })
}

module.exports = { fetchPopup, noteShown, noteConfirmed, allow, decorate }
