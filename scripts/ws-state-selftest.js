/**
 * 群聊 WebSocket 状态机回归测试（scripts/ws-state-selftest.js）
 * ------------------------------------------------------------------
 * 直接 require 真实的 subpackages/msg/utils/ws.js，用假 socket 驱动它的状态机。
 *
 * 覆盖的都是「线上收不到消息」的经典成因，而且都不会抛异常、只会静默失效：
 *   1. 握手卡死：connectSocket 既不 open 也不 close（网关没配 /api/ws、TCP 半开）
 *      → 老代码 state 永远停在 connecting，重连/降级全部不发生；
 *   2. 假死：切后台被系统掐断，onClose 不触发，state 还是 open
 *      → 老代码 isOpen() 返回 true，轮询永不启动；
 *   3. 降级后能不能自己回到实时通道（网络恢复）。
 *
 * 用法：node scripts/ws-state-selftest.js   （约 10 秒，因为要真等握手超时）
 */
const path = require('path')

/* ---------- 假 socket：手动控制 open / close / message ---------- */
function FakeSocket() {
  this.h = {}
  this.sent = []
}
FakeSocket.prototype.onOpen = function (fn) { this.h.open = fn }
FakeSocket.prototype.onMessage = function (fn) { this.h.message = fn }
FakeSocket.prototype.onClose = function (fn) { this.h.close = fn }
FakeSocket.prototype.onError = function (fn) { this.h.error = fn }
FakeSocket.prototype.send = function (m) { this.sent.push(m) }
FakeSocket.prototype.close = function () { /* 只记录，不回调：模拟真实 wx 的行为差异 */ }
FakeSocket.prototype.fireOpen = function () { this.h.open && this.h.open() }
FakeSocket.prototype.fireClose = function () { this.h.close && this.h.close() }
FakeSocket.prototype.fireMsg = function (obj) { this.h.message && this.h.message({ data: JSON.stringify(obj) }) }

let sockets = []
let ticketMode = 'ok'

global.wx = {
  connectSocket: function (opts) {
    const s = new FakeSocket()
    s.url = opts.url
    sockets.push(s)
    return s
  }
}

/* ---------- 依赖桩 ---------- */
const wsPath = path.resolve(__dirname, '../miniprogram/subpackages/msg/utils/ws.js')
function stub(relPath, exports) {
  const full = require.resolve(path.resolve(path.dirname(wsPath), relPath))
  require.cache[full] = { id: full, filename: full, loaded: true, exports: exports }
}
stub('../../../utils/config', { BASE_URL: 'https://example.com/api' })
stub('../../../utils/request', {
  post: function () {
    if (ticketMode === 'fail') return Promise.reject({ code: 500, msg: '取票失败' })
    return Promise.resolve({ ticket: 'tk-' + Date.now(), url: 'wss://example.com/api/ws?ticket=tk' })
  },
  get: function () { return Promise.resolve() }
})

const ws = require(wsPath)

/* ---------- 时钟：只推进业务代码看到的 Date.now，不影响 setTimeout ---------- */
const realNow = Date.now
let clockOffset = 0
Date.now = function () { return realNow.call(Date) + clockOffset }

let pass = 0
let fail = 0
function ok(cond, label) {
  if (cond) { pass++; console.log('  ✓ ' + label) } else { fail++; console.log('  ✗ ' + label) }
}
function wait(ms) { return new Promise(function (r) { setTimeout(r, ms) }) }
function reset() {
  ws.shutdown()
  sockets = []
  clockOffset = 0
  ticketMode = 'ok'
}

async function main() {
  /* ================= 用例 1：握手卡死 ================= */
  console.log('【1】握手卡死：connectSocket 既不 open 也不 close')
  reset()
  ws.connect()
  await wait(300)
  ok(sockets.length === 1, '已发起连接（1 条 socket）')
  ok(ws.getState() === 'connecting', '状态为 connecting')

  // 卡住不动 6 秒：不该提前放弃
  await wait(6000)
  ok(ws.getState() === 'connecting', '6 秒内仍在等待（不误判）')

  // 超过 8 秒握手超时 → 必须自我修复，重新发起连接
  await wait(3500)
  ok(sockets.length >= 2, '超过握手超时后重新发起连接（' + sockets.length + ' 条 socket）')
  ok(ws.getState() !== 'connecting' || sockets.length >= 2, '没有永久卡在 connecting')

  /* ================= 用例 2：正常打开 ================= */
  console.log('【2】握手成功：状态转 open，假死判定为否')
  reset()
  ws.connect()
  await wait(300)
  sockets[0].fireOpen()
  ok(ws.getState() === 'open', '状态为 open')
  ok(ws.isOpen() === true, 'isOpen() = true')
  ok(ws.isStale() === false, '刚打开的连接不算假死')

  /* ================= 用例 3：假死 ================= */
  console.log('【3】假死：连接开着，但很久没有任何帧')
  clockOffset += 50 * 1000
  ok(ws.isOpen() === true, 'state 仍是 open（onClose 没触发，正是线上的样子）')
  ok(ws.isStale() === true, 'isStale() = true（50 秒无帧）')

  const before = sockets.length
  ws.ensureAlive()
  await wait(1200)
  ok(sockets.length > before, 'ensureAlive 主动断开并重连（新增 socket）')

  /* ================= 用例 4：降级 ================= */
  console.log('【4】取票失败 → 降级，且降级后仍可被确保存活拉回')
  reset()
  ticketMode = 'fail'
  let degradedHit = 0
  ws.on('degrade', function () { degradedHit++ })
  ws.connect()
  await wait(500)
  ok(ws.getState() === 'degraded', '取票失败后进入 degraded（走轮询）')
  ok(degradedHit === 1, 'degrade 事件已通知页面（页面据此启轮询）')

  // 网络恢复：ensureAlive 应该给 WS 一次机会，而不是永远躺在降级里
  ticketMode = 'ok'
  ws.ensureAlive()
  await wait(500)
  ok(sockets.length >= 1, '降级后 ensureAlive 重新尝试了 WS')
  sockets[sockets.length - 1].fireOpen()
  ok(ws.getState() === 'open', '恢复后重新回到 open（实时通道）')

  /* ================= 用例 5：心跳帧刷新存活时间 ================= */
  console.log('【5】收到 ping 后不再算假死')
  clockOffset += 45 * 1000
  ok(ws.isStale() === true, '45 秒无帧 → 假死')
  sockets[sockets.length - 1].fireMsg({ type: 'ping' })
  ok(ws.isStale() === false, '收到 ping 后立即恢复为存活')
  ok(sockets[sockets.length - 1].sent.length >= 1, '收到 ping 回了 pong')

  /* ================= 用例 6：shutdown 清干净 ================= */
  console.log('【6】shutdown 后不再有残留定时器')
  reset()
  ok(ws.getState() === 'idle', 'shutdown 后回到 idle')
  ok(ws.isOpen() === false, 'isOpen() = false')

  console.log('')
  console.log((fail === 0 ? '✓ WS 状态机自测通过' : '✗ WS 状态机自测失败') +
    '：' + pass + ' 项通过 / ' + fail + ' 项失败')
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(function (e) {
  console.error('✗ 自测中断：', e && e.stack || e)
  process.exit(1)
})
