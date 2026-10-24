/**
 * 后端接口自检
 * 用法：node scripts/api-smoke-test.js [http://127.0.0.1:8888]
 *
 * 会依次请求公开接口、登录、登录后接口，逐条打印 状态码 / code / msg，
 * 一次性回答「是不是所有接口都有问题」。
 */
const http = require('http')
const https = require('https')
const { URL } = require('url')

const SERVER = (process.argv[2] || 'http://127.0.0.1:8888').replace(/\/$/, '')
const BASE = SERVER + '/api'

function request(method, path, body, token) {
  return new Promise(function (resolve) {
    const url = new URL(BASE + path)
    const lib = url.protocol === 'https:' ? https : http
    const payload = body ? JSON.stringify(body) : null
    const headers = { 'content-type': 'application/json' }
    if (token) headers.Authorization = 'Bearer ' + token
    if (payload) headers['content-length'] = Buffer.byteLength(payload)

    const req = lib.request(
      {
        protocol: url.protocol,
        hostname: url.hostname,
        port: url.port,
        path: url.pathname + url.search,
        method: method,
        headers: headers,
        timeout: 15000
      },
      function (res) {
        let raw = ''
        res.on('data', function (c) {
          raw += c
        })
        res.on('end', function () {
          let parsed = null
          try {
            parsed = JSON.parse(raw)
          } catch (e) {
            parsed = null
          }
          resolve({ status: res.statusCode, body: parsed, raw: raw })
        })
      }
    )
    req.on('timeout', function () {
      req.destroy()
      resolve({ status: 0, body: null, raw: 'timeout' })
    })
    req.on('error', function (e) {
      resolve({ status: -1, body: null, raw: e.message })
    })
    if (payload) req.write(payload)
    req.end()
  })
}

let pass = 0
let fail = 0
const failures = []

async function call(label, method, path, body, token) {
  const res = await request(method, path, body, token)
  const code = res.body && typeof res.body.code === 'number' ? res.body.code : null
  const ok = res.status === 200 && code === 0
  const msg = res.body && res.body.msg ? res.body.msg : res.raw.slice(0, 120)
  if (ok) {
    pass++
    console.log('  ok   ' + label + '  (' + res.status + ')')
  } else {
    fail++
    failures.push(label + ' → ' + res.status + ' / ' + msg)
    console.log('  FAIL ' + label + '  (HTTP ' + res.status + ', code=' + code + ')')
    if (msg) console.log('         ' + msg)
  }
  return res
}

async function main() {
  console.log('\n目标：' + BASE + '\n')

  console.log('[1] 公开接口（不需要登录）')
  await call('GET /schools', 'GET', '/schools')
  await call('GET /goods', 'GET', '/goods?page=1&size=2&sort=latest')
  await call('GET /goods/categories', 'GET', '/goods/categories')
  await call('GET /posts', 'GET', '/posts?page=1&size=2')
  await call('GET /channels', 'GET', '/channels')
  await call('GET /wall/posts', 'GET', '/wall/posts?page=1&size=2')
  // 关注 / 粉丝与个人主页：公开读（不带 token 也不应 401）
  await call('GET /user/{id}/profile', 'GET', '/user/9002/profile')
  await call('GET /user/follow/status', 'GET', '/user/follow/status?userId=9002')

  console.log('\n[2] 登录')
  const login = await call('POST /auth/login', 'POST', '/auth/login', {
    code: 'mock_code_for_smoke_test',
    platform: 'wx',
    devOpenid: 'dev_openid_guizhou_campus_001'
  })
  const token = login.body && login.body.data ? login.body.data.token : null
  if (!token) {
    console.log('  ! 拿不到 token，跳过登录后接口')
  } else {
    console.log('\n[3] 登录后接口')
    await call('GET /user/me', 'GET', '/user/me', null, token)
    await call('GET /goods/mine', 'GET', '/goods/mine', null, token)
    await call('GET /goods/favorites', 'GET', '/goods/favorites', null, token)
    await call('GET /goods/orders/mine', 'GET', '/goods/orders/mine?role=buyer', null, token)
    // 关注 / 粉丝与个人主页：状态查询 + 关注 + 取消（保持幂等，可重复执行）
    await call('GET /user/follow/status', 'GET', '/user/follow/status?userId=9002', null, token)
    await call('POST /user/follow/{id}', 'POST', '/user/follow/9002', null, token)
    await call('POST /user/follow/{id}（幂等）', 'POST', '/user/follow/9002', null, token)
    await call('DELETE /user/follow/{id}', 'DELETE', '/user/follow/9002', null, token)
    // 列表按作者过滤（个人主页内容聚合）
    await call('GET /posts?userId=', 'GET', '/posts?userId=9002&page=1&size=2', null, token)
    await call('GET /goods?userId=', 'GET', '/goods?userId=9002&page=1&size=2', null, token)

    const detail = await call('GET /goods/{id}', 'GET', '/goods/' + (await firstGoodsId()), null, token)
    if (detail.body && detail.body.data) {
      const id = detail.body.data.id
      await call('GET /wall/posts/{id}', 'GET', '/wall/posts/' + (await firstWallId()), null, token)
      await call('GET /wall/posts/{id}/comments', 'GET', '/wall/posts/' + (await firstWallId()) + '/comments', null, token)
      await call('GET /posts/{id}', 'GET', '/posts/' + (await firstPostId()), null, token)
      await call('GET /posts/{id}/comments', 'GET', '/posts/' + (await firstPostId()) + '/comments', null, token)
      // 商品评论（含楼中楼回复）：发一条顶层 + 一条回复，再删顶层（级联）
      const goodsId = detail.body.data.id
      await call('GET /goods/{id}/comments', 'GET', '/goods/' + goodsId + '/comments', null, token)
      const posted = await call('POST /goods/{id}/comments', 'POST', '/goods/' + goodsId + '/comments', {
        content: '冒烟测试：商品评论'
      }, token)
      const cid = posted.body && posted.body.data
      if (cid) {
        await call('POST /goods/{id}/comments（回复）', 'POST', '/goods/' + goodsId + '/comments', {
          content: '冒烟测试：楼中楼回复',
          parentId: cid
        }, token)
        await call('DELETE /goods/{id}/comments/{cid}', 'DELETE', '/goods/' + goodsId + '/comments/' + cid, null, token)
      }
      // 帖子评论回复
      const postId = await firstPostId()
      const pcomment = await call('POST /posts/{id}/comments', 'POST', '/posts/' + postId + '/comments', {
        content: '冒烟测试：帖子评论'
      }, token)
      const pcid = pcomment.body && pcomment.body.data
      if (pcid) {
        await call('POST /posts/{id}/comments（回复）', 'POST', '/posts/' + postId + '/comments', {
          content: '冒烟测试：帖子楼中楼回复',
          parentId: pcid
        }, token)
        await call('DELETE /posts/{id}/comments/{cid}', 'DELETE', '/posts/' + postId + '/comments/' + pcid, null, token)
      }
    }

    console.log('\n[4] 私信 / 消息中心')
    await call('GET /messages/unread', 'GET', '/messages/unread', null, token)
    const center = await call('GET /messages', 'GET', '/messages', null, token)
    const convos = center.body && center.body.data ? center.body.data.convos || [] : []
    if (!convos.length) {
      console.log('         （暂无会话，跳过会话内接口：可先执行 backend/sql/data.sql 导入演示会话）')
    } else {
      const cid = convos[0].id
      await call('GET /messages/{id}', 'GET', '/messages/' + cid, null, token)
      await call('GET /messages/{id}/messages', 'GET', '/messages/' + cid + '/messages', null, token)
      await call('POST /messages/{id}/read', 'POST', '/messages/' + cid + '/read', null, token)
      // 发一条 → 左滑撤回自己这条，闭环验证且不残留脏数据（撤回是软删除，
      // 消息行仍在库但对双方都不可见，也不会影响演示会话摘要）
      const sent = await call('POST /messages/{id}（发消息）', 'POST', '/messages/' + cid, { content: '冒烟测试消息' }, token)
      const sentMsgs = sent.body && sent.body.data ? sent.body.data.messages || [] : []
      const sentId = sentMsgs.length ? sentMsgs[sentMsgs.length - 1].id : null
      if (sentId) {
        await call('DELETE /messages/{id}/messages/{mid}（撤回）', 'DELETE',
          '/messages/' + cid + '/messages/' + sentId, null, token)
        // 撤回后再取一次，确认该消息已不可见
        const afterMsgs = await request('GET', '/messages/' + cid + '/messages', null, token)
        const list2 = afterMsgs.body && afterMsgs.body.data ? afterMsgs.body.data : []
        if (list2.every((m) => String(m.id) !== String(sentId))) {
          pass++
          console.log('  ok   撤回后消息不再返回')
        } else {
          fail++
          failures.push('DELETE /messages/{id}/messages/{mid} → 撤回后消息仍在返回')
          console.log('  FAIL 撤回后消息不再返回')
        }
      }
    }
    // 用「自己 vs 演示卖家」发起会话，校验 start 的复用逻辑
    await call('POST /messages/start', 'POST', '/messages/start', { peerId: 9002, goodsId: 8001, goodsTitle: '九成新机械键盘 87 键' }, token)

    console.log('\n[5] 左滑删除会话')
    // 删一个会话再验证它从列表消失；随后重新发起会话恢复（避免污染演示数据）
    const before = await call('GET /messages', 'GET', '/messages', null, token)
    const list = before.body && before.body.data ? before.body.data.convos || [] : []
    if (list.length) {
      const delId = list[list.length - 1].id
      await call('DELETE /messages/{id}（删除会话）', 'DELETE', '/messages/' + delId, null, token)
      const after = await call('GET /messages', 'GET', '/messages', null, token)
      const remain = after.body && after.body.data ? after.body.data.convos || [] : []
      if (remain.every((c) => String(c.id) !== String(delId))) {
        pass++
        console.log('  ok   删除后会话已从列表移除')
      } else {
        fail++
        failures.push('DELETE /messages/{id} → 会话仍在列表中')
        console.log('  FAIL 删除后会话已从列表移除')
      }
      // 重新发起会话，恢复被隐藏的会话（start 会自动取消隐藏）
      await call('POST /messages/start（恢复会话）', 'POST', '/messages/start',
        { peerId: list[list.length - 1].peerId, goodsId: list[list.length - 1].goodsCard ? list[list.length - 1].goodsCard.id : 0 }, token)
    }
  }

  console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
  if (failures.length) {
    console.log('\n失败明细：')
    failures.forEach(function (f) {
      console.log('  - ' + f)
    })
    console.log('\n常见原因：')
    console.log('  400 + "not specified ... -parameters"  → 后端没重新编译/重启（父 POM 已开启 -parameters）')
    console.log('  500 + SQL 相关                          → 数据库表不存在，请导入 backend/sql/*.sql')
    console.log('  -1 / timeout                            → 网关或服务未启动，检查 8888 端口与 Nacos')
  }
  process.exit(fail ? 1 : 0)
}

async function firstGoodsId() {
  const res = await request('GET', '/goods?page=1&size=1')
  return res.body && res.body.data && res.body.data.records.length ? res.body.data.records[0].id : 1
}
async function firstWallId() {
  const res = await request('GET', '/wall/posts?page=1&size=1')
  return res.body && res.body.data && res.body.data.records.length ? res.body.data.records[0].id : 1
}
async function firstPostId() {
  const res = await request('GET', '/posts?page=1&size=1')
  return res.body && res.body.data && res.body.data.records.length ? res.body.data.records[0].id : 1
}

main()
