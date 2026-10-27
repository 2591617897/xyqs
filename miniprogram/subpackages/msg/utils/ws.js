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
/** 握手超时定时器：wx.connectSocket 可能既不 open 也不 close（详见 connect） */
let connectTimer = null
/** 降级后的慢速重试定时器：降级 ≠ 永久放弃 */
let degradedTimer = null
/** 当前订阅的群（重连后自动恢复订阅，不用页面再调一次） */
const joined = {}
/** 事件表：type -> [fn] */
const handlers = {}

const MAX_RETRY_BEFORE_DEGRADE = 3
/**
 * 握手超时：connectSocket 到 TCP 半开 / 网关没配 /api/ws 这类情况时，
 * **onOpen 和 onClose 可能一个都不来**，state 会永远停在 connecting。
 * 那样 connect() 后续调用全部 return，重连不会触发、降级也不会触发，
 * 页面就彻底收不到消息，必须杀进程重进才能恢复 —— 这是最坑的一种。
 */
const CONNECT_TIMEOUT_MS = 8000
/** 降级为轮询后，每隔这么久再试一次 WS（网络好了要能自己回到实时通道） */
const DEGRADED_RETRY_MS = 30000
/**
 * 假死判定：超过这个时间没收到任何帧（含 ping）就认为连接已经死了。
 * 服务端 30 秒一次 ping，40 秒没帧必然是断的；比 watchdog 的 75 秒更快恢复。
 */
const STALE_MS = 40000

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

function clearConnectTimer() {
  if (connectTimer) {
    clearTimeout(connectTimer)
    connectTimer = null
  }
}

function clearDegradedTimer() {
  if (degradedTimer) {
    clearTimeout(degradedTimer)
    degradedTimer = null
  }
}

/** 握手超时兜底：到点还没 open 就当这次连接失败，走正常重试链路 */
function armConnectTimeout() {
  clearConnectTimer()
  connectTimer = setTimeout(function () {
    connectTimer = null
    if (state !== 'connecting') return
    console.warn('[ws] 握手超时（' + CONNECT_TIMEOUT_MS + 'ms 未 open），按失败处理')
    closeSocket()
    state = 'idle'
    scheduleReconnect()
  }, CONNECT_TIMEOUT_MS)
}

/**
 * 降级后仍定期尝试 WS。
 *
 * <p>降级的语义是「先用轮询保证能用」，不是「这辈子不再试 WS」：
 * 用户从电梯里出来、Wi-Fi 恢复、网关重启完成，都应该自动回到实时通道。
 */
function scheduleDegradedRetry() {
  if (degradedTimer) return
  degradedTimer = setTimeout(function () {
    degradedTimer = null
    if (state !== 'degraded') return
    console.log('[ws] 降级中，重试 WebSocket')
    state = 'idle'
    retry = 0
    connect()
  }, DEGRADED_RETRY_MS)
}

function startWatchdog() {
  if (watchdogTimer) clearInterval(watchdogTimer)
  lastAliveAt = Date.now()
  watchdogTimer = setInterval(function () {
    if (state !== 'open') return
    // 和 isStale() 用同一个标准：两边判断不一致的话，会出现
    // 「页面觉得连接还活着所以不开轮询，watchdog 也觉得没事」的死局
    if (isStale()) {
      // 长时间没有任何帧（连 ping 都没来）→ 判定死连接，主动关掉触发重连
      console.warn('[ws] 心跳超时，主动断开重连')
      closeSocket()
      state = 'idle'
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
  // 先上超时再说：取票请求本身也可能挂住（网关假死、DNS 卡住）
  armConnectTimeout()

  http.post('/groups/ws-ticket', null, { silent: true }).then(function (res) {
    const ticket = res && res.ticket
    if (!ticket) throw new Error('未拿到票据')
    const url = (res && res.url) || wsUrl(ticket)
    openSocket(url)
  }).catch(function (err) {
    // 拿不到票据（未登录 / 服务未起）→ 直接降级，不做无意义重试
    console.warn('[ws] 取票失败，降级为轮询', err && err.msg)
    clearConnectTimer()
    state = 'degraded'
    emit('degrade')
    scheduleDegradedRetry()
  })
}

function openSocket(url) {
  socket = wx.connectSocket({ url: url })

  socket.onOpen(function () {
    state = 'open'
    retry = 0
    // 握手成功 → 撤掉超时兜底，并取消降级期的慢重试（已经回到实时通道了）
    clearConnectTimer()
    clearDegradedTimer()
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
    const wasDegraded = state === 'degraded'
    state = wasDegraded ? 'degraded' : 'idle'
    socket = null
    clearConnectTimer()
    emit('close', { wasOpen: wasOpen })
    // 已降级（靠轮询兜底）时不再走快速重连，交给 scheduleDegradedRetry 的慢重试
    if (!wasDegraded) scheduleReconnect()
  })

  socket.onError(function () {
    // onClose 一般会跟着触发，这里只记日志；真正的重试逻辑在 onClose
    console.warn('[ws] 连接出错')
  })
}

function scheduleReconnect() {
  clearTimers()
  clearConnectTimer()
  retry++
  if (retry > MAX_RETRY_BEFORE_DEGRADE && state !== 'degraded') {
    state = 'degraded'
    emit('degrade')
    scheduleDegradedRetry()
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
  // 注意：必须原样带上 replyToId —— 引用回复只走这一个字段，
  // 之前漏传导致「针对回复」在 WebSocket 主路径下完全失效
  // （HTTP 兜底路径 sendByHttp 带了，所以只有弱网降级时才偶尔能回显引用）。
  return rawSend({
    type: 'msg',
    groupId: String(groupId),
    msgType: payload.msgType || 'text',
    content: payload.content || '',
    clientMsgId: payload.clientMsgId || '',
    atUserIds: payload.atUserIds || [],
    atAll: !!payload.atAll,
    replyToId: payload.replyToId || ''
  })
}

/** 页面退出时调用：关连接并清空所有状态，避免下一个页面复用到已死的连接 */
function shutdown() {
  clearTimers()
  clearConnectTimer()
  clearDegradedTimer()
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

/** 连接开着但长时间没收到任何帧 → 假死（切后台被系统掐断时 onClose 往往不触发） */
function isStale() {
  if (state !== 'open') return true
  return Date.now() - lastAliveAt > STALE_MS
}

/**
 * 「确保可用」——页面回到前台时调一次。
 *
 * <p>小程序切后台超过几秒，系统会静默掐掉 WS，但**不会触发 onClose**，
 * state 仍停在 open。此时 isOpen() 返回 true，页面就不会启轮询，
 * 用户看到的正是「别人发消息我这边毫无反应」。
 *
 * <p>这里分三种情况处理：
 *   open 但假死 → 主动断开重连（同时让页面先开轮询兜住这段空窗）
 *   connecting  → 不动，交给 armConnectTimeout 判超时
 *   idle/degraded → 立即重连（degraded 也给一次机会回到实时通道）
 */
function ensureAlive() {
  if (state === 'open') {
    if (!isStale()) return
    console.warn('[ws] 连接假死（' + Math.round((Date.now() - lastAliveAt) / 1000) + 's 无帧），强制重连')
    closeSocket()
    state = 'idle'
    retry = 0
    scheduleReconnect()
    return
  }
  if (state === 'connecting') return
  // degraded 也要给一次机会：网络恢复后不该永远停在轮询
  if (state === 'degraded') {
    clearDegradedTimer()
    state = 'idle'
    retry = 0
  }
  connect()
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
  isStale,
  ensureAlive,
  getState
}
