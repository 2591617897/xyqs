/**
 * 群聊 WebSocket 客户端（单例）。
 *
 * 三个必须做对的点，改这个文件前先读一遍：
 *  1. 断线重连成功后必须**补拉** sinceId，否则断线期间的消息永久丢失
 *     —— 这是 WebSocket 相比轮询唯一真正的短板；
 *  2. 连续 3 次连不上要**降级**成 HTTP 轮询，保证弱网下功能不瘫；
 *  3. onHide / onUnload 必须关连接 + 清定时器，否则后台持续占连接、耗电（E-09）。
 *
 * 鉴权：先 POST /groups/ws-ticket 换一次性票据（60 秒、用一次即废），
 * 不把 JWT 拼进 URL —— Nginx 的 access_log 会记录完整 URL，token 落进日志等于泄露。
 */
const { BASE_URL } = require('../../../utils/config')
const http = require('../../../utils/request')

/** 连接状态：idle 未连 / connecting / open / degraded（已降级为轮询） */
let state = 'idle'
let socket = null
let retry = 0
let retryTimer = null
let watchdogTimer = null
let lastAliveAt = 0
/** 当前订阅的群（重连后自动恢复订阅，不用页面再调一次） */
const joined = {}
/** 事件表：type -> [fn] */
const handlers = {}

const MAX_RETRY_BEFORE_DEGRADE = 3
/** 服务端 30s 一次 ping，这里 75s 收不到任何帧就认为连接已死 */
const WATCHDOG_MS = 75000

function wsUrl(ticket) {
  // BASE_URL 形如 https://host/api → wss://host/api，再拼 /ws
  return BASE_URL.replace(/^http/, 'ws') + '/ws?ticket=' + encodeURIComponent(ticket)
}

function on(type, fn) {
  if (typeof fn !== 'function') return
  if (!handlers[type]) handlers[type] = []
  handlers[type].push(fn)
}

function off(type, fn) {
  const list = handlers[type]
  if (!list) return
  if (!fn) {
    delete handlers[type]
    return
  }
  const i = list.indexOf(fn)
  if (i >= 0) list.splice(i, 1)
}

function emit(type, payload) {
  const list = handlers[type]
  if (!list || !list.length) return
  // 拷贝一份再遍历：回调里注销自己时不会打乱循环
  list.slice().forEach(function (fn) {
    try {
      fn(payload)
    } catch (e) {
      console.warn('[ws] 回调异常', type, e)
    }
  })
}

function clearTimers() {
  if (retryTimer) {
    clearTimeout(retryTimer)
    retryTimer = null
  }
  if (watchdogTimer) {
    clearInterval(watchdogTimer)
    watchdogTimer = null
  }
}

function startWatchdog() {
  if (watchdogTimer) clearInterval(watchdogTimer)
  lastAliveAt = Date.now()
  watchdogTimer = setInterval(function () {
    if (state !== 'open') return
    if (Date.now() - lastAliveAt > WATCHDOG_MS) {
      // 长时间没有任何帧（连 ping 都没来）→ 判定死连接，主动关掉触发重连
      console.warn('[ws] 心跳超时，主动断开重连')
      closeSocket()
      scheduleReconnect()
    }
  }, 15000)
}

function closeSocket() {
  if (!socket) return
  try {
    socket.close({ code: 1000 })
  } catch (e) {
    /* 已关闭时忽略 */
  }
  socket = null
}

/**
 * 建立连接。已连或正在连时直接返回，避免页面 onShow 连出多条。
 */
function connect() {
  if (state === 'open' || state === 'connecting') return
  state = 'connecting'

  http.post('/groups/ws-ticket', null, { silent: true }).then(function (res) {
    const ticket = res && res.ticket
    if (!ticket) throw new Error('未拿到票据')
    const url = (res && res.url) || wsUrl(ticket)
    openSocket(url)
  }).catch(function (err) {
    // 拿不到票据（未登录 / 服务未起）→ 直接降级，不做无意义重试
    console.warn('[ws] 取票失败，降级为轮询', err && err.msg)
    state = 'degraded'
    emit('degrade')
  })
}

function openSocket(url) {
  socket = wx.connectSocket({ url: url })

  socket.onOpen(function () {
    state = 'open'
    retry = 0
    startWatchdog()
    // 重连后恢复订阅（页面不用关心）
    Object.keys(joined).forEach(function (gid) {
      rawSend({ type: 'join', groupId: gid })
    })
    emit('open')
  })

  socket.onMessage(function (res) {
    lastAliveAt = Date.now()
    let msg
    try {
      msg = JSON.parse(res.data)
    } catch (e) {
      return
    }
    if (msg.type === 'ping') {
      rawSend({ type: 'pong' })
      return
    }
    if (msg.type === 'pong') return
    emit(msg.type, msg)
  })

  socket.onClose(function () {
    const wasOpen = state === 'open'
    state = 'idle'
    socket = null
    emit('close', { wasOpen: wasOpen })
    scheduleReconnect()
  })

  socket.onError(function () {
    // onClose 一般会跟着触发，这里只记日志；真正的重试逻辑在 onClose
    console.warn('[ws] 连接出错')
  })
}

function scheduleReconnect() {
  clearTimers()
  retry++
  if (retry > MAX_RETRY_BEFORE_DEGRADE && state !== 'degraded') {
    state = 'degraded'
    emit('degrade')
    return
  }
  // 指数退避 1s → 2s → 4s → 8s，封顶 15s（别让用户等太久看到消息）
  const delay = Math.min(1000 * Math.pow(2, retry - 1), 15000)
  retryTimer = setTimeout(function () {
    state = 'idle'
    connect()
  }, delay)
}

function rawSend(frame) {
  if (state !== 'open' || !socket) return false
  try {
    socket.send({ data: JSON.stringify(frame) })
    return true
  } catch (e) {
    console.warn('[ws] 发送失败', e)
    return false
  }
}

/** 订阅某个群（记住状态，重连后自动恢复） */
function join(groupId) {
  const gid = String(groupId)
  joined[gid] = true
  rawSend({ type: 'join', groupId: gid })
}

function leave(groupId) {
  const gid = String(groupId)
  delete joined[gid]
  rawSend({ type: 'leave', groupId: gid })
}

/**
 * 发消息。返回 true 表示已交给 WS；
 * 返回 false 表示当前不可用，调用方应改走 HTTP（保证弱网也能发出去）。
 */
function sendMsg(groupId, payload) {
  return rawSend({
    type: 'msg',
    groupId: String(groupId),
    msgType: payload.msgType || 'text',
    content: payload.content || '',
    clientMsgId: payload.clientMsgId || '',
    atUserIds: payload.atUserIds || [],
    atAll: !!payload.atAll
  })
}

/** 页面退出时调用：关连接并清空所有状态，避免下一个页面复用到已死的连接 */
function shutdown() {
  clearTimers()
  Object.keys(joined).forEach(function (k) {
    delete joined[k]
  })
  closeSocket()
  state = 'idle'
  retry = 0
}

function isOpen() {
  return state === 'open'
}

function getState() {
  return state
}

module.exports = {
  connect,
  shutdown,
  join,
  leave,
  sendMsg,
  on,
  off,
  isOpen,
  getState
}
