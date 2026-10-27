/**
 * 群聊实时通道（WebSocket）端到端自测。
 *
 * <p>用两个真实账号连上网关的 /api/ws，A 发一条、等 B 收到，验证：
 *   1. 票据接口可用、握手通过
 *   2. join 订阅生效
 *   3. 广播能把消息推给「另一个用户」的连接（而不只是发送者自己）
 *
 * <p>之所以要两个账号：只用一条连接测，自己发的消息本来就会广播回自己，
 * 「别人收不到」这个 bug 会完全测不出来。
 *
 * 用法：node scripts/ws-live-selftest.js [groupId]
 * 依赖本地 8106 + 网关 8888 已启动（SERVER__PORT=8106 mvn spring-boot:run）。
 *
 * ⚠️ 会往群里真发 2 条消息（内容以「ws实测」开头）。跑完要清理：
 *    delete from t_group_message where content like 'ws实测%';
 *    并把 t_group.last_msg_id / t_group_member.last_read_id 回填到剩余最大 id。
 */
const http = require('http')

const GW = 'http://localhost:8888/api'
const WS_BASE = 'ws://localhost:8888/api/ws'
const GROUP_ID = process.argv[2] || '2114979847825575938'
const USER_A = '2114022012375531521'
const USER_B = '2114018677295706113'
const SCHOOL = '1146'

function token(userId) {
  const { execSync } = require('child_process')
  const out = execSync('node scripts/make-dev-token.js ' + userId + ' ' + SCHOOL + ' 2', {
    cwd: require('path').join(__dirname, '..'),
    encoding: 'utf8'
  })
  return out.trim().split('\n').pop().trim()
}

function call(path, method, tok, body) {
  return new Promise(function (resolve, reject) {
    const data = body ? JSON.stringify(body) : ''
    const req = http.request(GW + path, {
      method: method || 'GET',
      headers: Object.assign(
        { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) },
        tok ? { Authorization: 'Bearer ' + tok } : {}
      )
    }, function (res) {
      let buf = ''
      res.on('data', function (c) { buf += c })
      res.on('end', function () {
        try { resolve(JSON.parse(buf)) } catch (e) { reject(new Error('非 JSON：' + buf.slice(0, 200))) }
      })
    })
    req.on('error', reject)
    if (data) req.write(data)
    req.end()
  })
}

function wait(ms) { return new Promise(function (r) { setTimeout(r, ms) }) }

/** 连一条 WS，返回 { ws, frames } —— frames 收集所有下行帧便于断言 */
function connect(name, tok) {
  return call('/groups/ws-ticket', 'POST', tok).then(function (res) {
    if (!res || res.code !== 0 || !res.data || !res.data.ticket) {
      throw new Error(name + ' 取票失败：' + JSON.stringify(res).slice(0, 200))
    }
    const url = WS_BASE + '?ticket=' + encodeURIComponent(res.data.ticket)
    const ws = new WebSocket(url)
    const frames = []
    ws.addEventListener('message', function (ev) {
      let f = null
      try { f = JSON.parse(ev.data) } catch (e) { return }
      frames.push(f)
    })
    return new Promise(function (resolve, reject) {
      const timer = setTimeout(function () { reject(new Error(name + ' 握手超时')) }, 8000)
      ws.addEventListener('open', function () {
        clearTimeout(timer)
        console.log('  ✓ ' + name + ' 已连接')
        resolve({ name: name, ws: ws, frames: frames })
      })
      ws.addEventListener('error', function (e) {
        clearTimeout(timer)
        reject(new Error(name + ' 连接失败：' + (e && e.message)))
      })
    })
  })
}

let pass = 0
let fail = 0
function ok(cond, label) {
  if (cond) { pass++; console.log('  ✓ ' + label) } else { fail++; console.log('  ✗ ' + label) }
}

async function main() {
  const tokA = token(USER_A)
  const tokB = token(USER_B)
  console.log('【1】建立两条独立连接（模拟两个用户）')
  const A = await connect('A(发送方)', tokA)
  const B = await connect('B(接收方)', tokB)

  console.log('【2】双方订阅同一个群')
  A.ws.send(JSON.stringify({ type: 'join', groupId: GROUP_ID }))
  B.ws.send(JSON.stringify({ type: 'join', groupId: GROUP_ID }))
  await wait(1000)
  const joinedA = A.frames.filter(function (f) { return f.type === 'joined' })
  const joinedB = B.frames.filter(function (f) { return f.type === 'joined' })
  ok(joinedA.length === 1, 'A join 收到 joined 回执')
  ok(joinedB.length === 1, 'B join 收到 joined 回执')
  if (!joinedA.length || !joinedB.length) {
    console.log('  ✗ join 失败，后续无法继续'); process.exit(1)
  }

  console.log('【3】A 通过 WebSocket 发一条消息')
  const marker = 'ws实测' + Date.now()
  A.ws.send(JSON.stringify({
    type: 'msg', groupId: GROUP_ID, msgType: 'text', content: marker, clientMsgId: 'selftest-' + Date.now()
  }))
  await wait(4000)

  console.log('  · A 期间收到全部帧：' + JSON.stringify(A.frames.filter(function(f){return f.type!=='pong'}).map(function(f){return f.type + (f.code?('#'+f.code+' '+f.msg):'') + (f.message?(':'+String(f.message.content||'').slice(0,30)):'')})))
  const aGot = A.frames.filter(function (f) { return f.type === 'msg' && f.message && f.message.content === marker })
  const bGot = B.frames.filter(function (f) { return f.type === 'msg' && f.message && f.message.content === marker })

  ok(aGot.length === 1, 'A 收到自己消息的广播（回显）')
  ok(bGot.length >= 1, 'B 收到 A 的消息（关键：跨连接广播）')

  if (bGot.length) {
    const m = bGot[0].message
    console.log('  · B 收到的帧：id=' + m.id + ' type=' + (m.type || m.msgType) + ' sender=' + m.senderId + ' content=' + m.content)
    ok(String(m.senderId) === USER_A, 'B 收到的 senderId 是 A')
    ok(!!m.id, 'B 收到的消息带 id（前端按 id 去重依赖它）')
  }

  console.log('【4】B 通过 HTTP 发一条，验证反向 + A 能收到')
  const marker2 = 'ws实测反' + Date.now()
  await call('/groups/' + GROUP_ID + '/messages', 'POST', tokB, { content: marker2, clientMsgId: 'selftest-http-' + Date.now() })
  await wait(4000)
  const aGot2 = A.frames.filter(function (f) { return f.type === 'msg' && f.message && f.message.content === marker2 })
  ok(aGot2.length >= 1, 'A 收到 B 走 HTTP 发出的消息（HTTP 发送也会广播）')

  A.ws.close()
  B.ws.close()
  console.log('')
  console.log((fail === 0 ? '✓ 实时通道自测通过' : '✗ 实时通道自测失败') + '：' + pass + ' 项通过 / ' + fail + ' 项失败')
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(function (e) {
  console.error('✗ 自测中断：', e.message)
  process.exit(1)
})
