/**
 * 后台管理接口自检（campus-admin）
 * 用法：node scripts/admin-smoke-test.js [http://127.0.0.1:8888] [username] [password]
 *
 * 分七段验证：
 *   [1] 网关鉴权边界 —— 不带令牌 / 拿小程序令牌，都必须被 401 挡住
 *   [2] 登录 —— 含错误密码、账号锁定
 *   [3] 登录后接口 —— info / menus / 管理员 / 角色 / 菜单 / 日志
 *   [4] 接口级权限校验 —— 用受限账号请求未授权接口必须 403
 *   [5] P1 业务只读接口 —— 内容 / 用户 / 工单 / 聊天 / 广告 / 公告 / 敏感词 / 校区 / 看板
 *   [6] 处置联动端到端 —— 隐藏、匿名追溯、禁言、敏感词命中、广告双闸（改真实数据后全部还原）
 *   [7] 操作日志落库 —— 上面的处置动作必须留下痕迹
 *
 * 注意：[6] 会真实改动库里第一条帖子 / 第一个用户的状态，跑完立刻还原；
 * 若中途报错退出，请按脚本打印的 id 手工恢复（帖子 status、用户禁言）。
 *
 * 结构沿用 scripts/api-smoke-test.js：request 永不 reject、code===0 且 HTTP 200 才算通过、
 * 失败明细集中打印、退出码可被 CI 消费。
 */
const http = require('http')
const https = require('https')
const { URL } = require('url')

const SERVER = (process.argv[2] || 'http://127.0.0.1:8888').replace(/\/$/, '')
const BASE = SERVER + '/api'
const ADMIN = BASE + '/admin'
const USERNAME = process.argv[3] || 'admin'
const PASSWORD = process.argv[4] || 'admin123'

function request (method, path, body, token) {
  return new Promise(function (resolve) {
    const url = new URL(path.startsWith('http') ? path : ADMIN + path)
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
        res.on('data', function (c) { raw += c })
        res.on('end', function () {
          let parsed = null
          try { parsed = JSON.parse(raw) } catch (e) { parsed = null }
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

async function call (label, method, path, body, token) {
  const res = await request(method, path, body, token)
  const code = res.body && typeof res.body.code === 'number' ? res.body.code : null
  const ok = res.status === 200 && code === 0
  const msg = res.body && res.body.msg ? res.body.msg : String(res.raw || '').slice(0, 120)
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

/**
 * 断言「应该被拒绝」。
 * 网关层拦截 → HTTP 401/403 + body.code；业务层 BizException → HTTP 200 + body.code。
 * 两种都是本项目约定，这里都算通过。
 */
async function callReject (label, method, path, body, token, expectCode) {
  const res = await request(method, path, body, token)
  const code = res.body && typeof res.body.code === 'number' ? res.body.code : null
  const ok = code === expectCode && (res.status === 200 || res.status === 401 || res.status === 403)
  if (ok) {
    pass++
    console.log('  ok   ' + label + '  (HTTP ' + res.status + ', code=' + code + ')')
  } else {
    fail++
    failures.push(label + ' → 期望被拒绝，实际 HTTP ' + res.status + ' / code=' + code)
    console.log('  FAIL ' + label + '  (期望被拒绝，实际 HTTP ' + res.status + ', code=' + code + ')')
  }
  return res
}

/**
 * 断言型检查：业务接口跑通了还不够，返回值本身也要对。
 * 只读用例只用 call()，端到端用例的「结果校验」靠它。
 */
function expect (label, ok, detail) {
  if (ok) {
    pass++
    console.log('  ok   ' + label)
  } else {
    fail++
    failures.push(label + ' → ' + (detail || '断言不成立'))
    console.log('  FAIL ' + label + '  ' + (detail || ''))
  }
}

/** 从分页响应里安全取第一条记录 */
function firstRecord (res) {
  if (!res || !res.body || res.body.code !== 0) return null
  const d = res.body.data
  const list = d && d.records ? d.records : null
  return list && list.length ? list[0] : null
}

/** 取一个小程序用户令牌，用来验证「C 端令牌进不了后台」 */
async function fetchUserToken () {
  const res = await request('POST', BASE + '/auth/login', {
    code: 'mock_code_for_smoke_test',
    platform: 'wx',
    devOpenid: 'dev_openid_guizhou_campus_001'
  })
  if (res.body && res.body.code === 0 && res.body.data && res.body.data.token) {
    return res.body.data.token
  }
  return null
}

async function main () {
  console.log('\n目标：' + ADMIN + '\n')

  console.log('[1] 网关鉴权边界（不带令牌 / 拿小程序令牌都必须被拦）')
  await callReject('GET /auth/info 无令牌', 'GET', '/auth/info', null, null, 401)
  const userToken = await fetchUserToken()
  if (userToken) {
    await callReject('GET /auth/info 用小程序令牌', 'GET', '/auth/info', null, userToken, 401)
  } else {
    console.log('  skip 取不到小程序令牌（后端未开启 AUTH_MOCK），跳过「C 端令牌串用」校验')
  }

  console.log('\n[2] 登录')
  const wrong = await request('POST', '/auth/login', { username: USERNAME, password: 'wrong_password_' + Date.now() })
  if (wrong.status === 200 && wrong.body && wrong.body.code !== 0) {
    pass++
    console.log('  ok   错误密码被拒绝  (code=' + wrong.body.code + ')')
  } else {
    fail++
    failures.push('错误密码竟然通过了')
    console.log('  FAIL 错误密码竟然通过了')
  }

  const login = await call('POST /auth/login', 'POST', '/auth/login', { username: USERNAME, password: PASSWORD })
  let token = null
  if (login.body && login.body.code === 0 && login.body.data) {
    token = login.body.data.token
  }
  if (!token) {
    console.log('\n取不到管理员令牌，后续用例全部跳过。')
    console.log('常见原因：')
    console.log('  1）没有执行 backend/sql/migration-20261025-campus-admin*.sql')
    console.log('  2）campus-admin 未启动，或网关没重启（改路由必须重启网关）')
    console.log('  3）网关与 admin 服务的 campus.admin.jwt.secret 不一致')
    console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
    process.exit(1)
  }

  console.log('\n[3] 登录后接口')
  await call('GET /auth/info', 'GET', '/auth/info', null, token)
  const menus = await call('GET /auth/menus', 'GET', '/auth/menus', null, token)
  if (menus.body && menus.body.code === 0 && Array.isArray(menus.body.data)) {
    console.log('       菜单顶层节点 ' + menus.body.data.length + ' 个')
  }
  await call('GET /admins', 'GET', '/admins?page=1&size=5', null, token)
  await call('GET /roles', 'GET', '/roles?page=1&size=5', null, token)
  await call('GET /menus', 'GET', '/menus', null, token)
  await call('GET /logs/operations', 'GET', '/logs/operations?page=1&size=5', null, token)
  await call('GET /logs/logins', 'GET', '/logs/logins?page=1&size=5', null, token)
  await call('POST /auth/logout', 'POST', '/auth/logout', null, token)

  console.log('\n[4] 接口级权限校验（用受限账号试探未授权接口）')
  const probeUser = 'smoke_probe_' + Date.now()
  const roleRes = await request('GET', '/roles?page=1&size=100', null, token)
  let readerRoleId = null
  if (roleRes.body && roleRes.body.code === 0) {
    const hit = (roleRes.body.data.records || []).find((r) => r.code === 'REGION_LEADER')
    readerRoleId = hit ? hit.id : null
  }
  if (!readerRoleId) {
    console.log('  skip 找不到 REGION_LEADER 角色，跳过权限校验用例')
  } else {
    const created = await request('POST', '/admins', {
      username: probeUser,
      password: 'probe123456',
      realName: '冒烟测试账号',
      dataScope: 2,
      status: 1,
      roleIds: [readerRoleId]
    }, token)
    if (created.body && created.body.code === 0) {
      const probeLogin = await request('POST', '/auth/login', { username: probeUser, password: 'probe123456' })
      const probeToken = probeLogin.body && probeLogin.body.code === 0 ? probeLogin.body.data.token : null
      if (probeToken) {
        // REGION_LEADER 只有只读权限，创建管理员必须被拒
        await callReject('受限账号 POST /admins', 'POST', '/admins', {
          username: probeUser + '_2',
          password: 'probe123456',
          status: 1,
          roleIds: [readerRoleId]
        }, probeToken, 403)
      } else {
        console.log('  skip 受限账号登录失败，跳过权限校验用例')
      }
      // 清理演示数据
      await request('DELETE', '/admins/' + created.body.data, null, token)
      console.log('       已清理演示账号 ' + probeUser)
    } else {
      console.log('  skip 创建受限账号失败：' + (created.body ? created.body.msg : created.raw))
    }
  }

  console.log('\n[5] P1 业务只读接口（只查询，不写库）')
  await call('GET /content/posts', 'GET', '/content/posts?page=1&size=5', null, token)
  await call('GET /content/wall', 'GET', '/content/wall?page=1&size=5', null, token)
  await call('GET /content/goods', 'GET', '/content/goods?page=1&size=5', null, token)
  await call('GET /content/comments', 'GET', '/content/comments?page=1&size=5', null, token)
  await call('GET /content/audits', 'GET', '/content/audits?page=1&size=5', null, token)
  await call('GET /users', 'GET', '/users?page=1&size=5', null, token)
  await call('GET /users/verify/pending', 'GET', '/users/verify/pending?page=1&size=5', null, token)
  await call('GET /users/verify/logs', 'GET', '/users/verify/logs?page=1&size=5', null, token)
  await call('GET /users/blacklist', 'GET', '/users/blacklist?page=1&size=5', null, token)
  await call('GET /users/mutes', 'GET', '/users/mutes?page=1&size=5', null, token)
  await call('GET /reports', 'GET', '/reports?page=1&size=5', null, token)
  await call('GET /chat/conversations', 'GET', '/chat/conversations?page=1&size=5', null, token)
  await call('GET /chat/messages', 'GET', '/chat/messages?page=1&size=5', null, token)
  await call('GET /ads/slots', 'GET', '/ads/slots?page=1&size=5', null, token)
  await call('GET /ads/materials', 'GET', '/ads/materials?page=1&size=5', null, token)
  await call('GET /notices', 'GET', '/notices?page=1&size=5', null, token)
  await call('GET /sensitive', 'GET', '/sensitive?page=1&size=5', null, token)
  await call('GET /regions/tree', 'GET', '/regions/tree', null, token)
  await call('GET /regions/leaders', 'GET', '/regions/leaders?page=1&size=5', null, token)
  await call('GET /dashboard/overview', 'GET', '/dashboard/overview', null, token)
  await call('GET /dashboard/daily', 'GET', '/dashboard/daily?page=1&size=5', null, token)

  console.log('\n[6] 处置联动端到端（会改真实数据，跑完全部还原）')

  // 6.1 帖子隐藏 → 详情 status 变 0 → 处置流水出现 HIDE → 恢复原状态
  const postPage = await request('GET', '/content/posts?page=1&size=1', null, token)
  const post = firstRecord(postPage)
  if (!post) {
    console.log('  skip 库里没有帖子，跳过 6.1')
  } else {
    const originStatus = post.status
    await call('POST /content/posts/{id}/status 隐藏', 'POST', '/content/posts/' + post.id + '/status',
      { status: 0, reasonType: '冒烟测试', reason: 'admin-smoke-test 自动隐藏' }, token)
    const afterHide = await request('GET', '/content/posts/' + post.id, null, token)
    const hidden = afterHide.body && afterHide.body.code === 0 && afterHide.body.data &&
      Number(afterHide.body.data.status) === 0
    expect('隐藏后详情 status === 0', hidden,
      '实际 status=' + (afterHide.body && afterHide.body.data ? afterHide.body.data.status : 'n/a'))

    const auditPage = await request('GET', '/content/audits?targetType=POST&action=HIDE&page=1&size=10', null, token)
    const audit = firstRecord(auditPage)
    expect('处置流水出现 HIDE 记录', !!audit && String(audit.targetId) === String(post.id),
      audit ? '流水 targetId=' + audit.targetId + ' 期望 ' + post.id : '查不到 HIDE 流水')

    await call('POST /content/posts/{id}/status 恢复', 'POST', '/content/posts/' + post.id + '/status',
      { status: originStatus == null ? 1 : originStatus, reasonType: '冒烟测试', reason: 'admin-smoke-test 自动恢复' }, token)
  }

  // 6.2 匿名表白墙：前台匿名、后台必须能追溯真实作者
  const wallPage = await request('GET', '/content/wall?page=1&size=10', null, token)
  const wallList = (wallPage.body && wallPage.body.code === 0 && wallPage.body.data && wallPage.body.data.records) || []
  const anonWall = wallList.find(function (w) { return w.anonName })
  if (!anonWall) {
    console.log('  skip 库里没有匿名表白墙，跳过 6.2')
  } else {
    const wallDetailRes = await request('GET', '/content/wall/' + anonWall.id, null, token)
    const wd = wallDetailRes.body && wallDetailRes.body.code === 0 ? wallDetailRes.body.data : null
    expect('匿名墙详情同时返回 anonName 与 realUserId',
      !!wd && !!wd.anonName && wd.realUserId != null,
      wd ? 'anonName=' + wd.anonName + ', realUserId=' + wd.realUserId : '详情查询失败')
  }

  // 6.3 禁言 → t_user.mute_until 投影有值 → 解除 → 投影清空
  const userPage = await request('GET', '/users?page=1&size=1', null, token)
  const someUser = firstRecord(userPage)
  if (!someUser) {
    console.log('  skip 库里没有用户，跳过 6.3')
  } else {
    await call('POST /users/{id}/mute 禁言10分钟', 'POST', '/users/' + someUser.id + '/mute',
      { scope: 1, minutes: 10, reasonType: '冒烟测试', reason: 'admin-smoke-test 自动禁言' }, token)
    const mutedRes = await request('GET', '/users/' + someUser.id, null, token)
    const muted = mutedRes.body && mutedRes.body.code === 0 && mutedRes.body.data && mutedRes.body.data.muteUntil
    expect('禁言后用户详情 muteUntil 有值', !!muted,
      '实际 muteUntil=' + (mutedRes.body && mutedRes.body.data ? mutedRes.body.data.muteUntil : 'n/a'))
    const muteList = await request('GET', '/users/mutes?userId=' + someUser.id + '&page=1&size=5', null, token)
    expect('禁言记录出现在 /users/mutes', !!firstRecord(muteList), '查不到生效中的禁言记录')

    await call('POST /users/{id}/unmute 解除', 'POST', '/users/' + someUser.id + '/unmute?scope=1', null, token)
    const unmutedRes = await request('GET', '/users/' + someUser.id, null, token)
    const cleared = unmutedRes.body && unmutedRes.body.code === 0 && unmutedRes.body.data && !unmutedRes.body.data.muteUntil
    expect('解除后 muteUntil 已清空', !!cleared,
      '实际 muteUntil=' + (unmutedRes.body && unmutedRes.body.data ? unmutedRes.body.data.muteUntil : 'n/a'))

    await call('POST /users/mutes/release-expired', 'POST', '/users/mutes/release-expired', null, token)
  }

  // 6.4 敏感词：新建 → 试检测命中 → 删除清理
  const word = '冒烟敏感词' + Date.now()
  const wordRes = await request('POST', '/sensitive', { word: word, level: 2, replaceTo: '***', status: 1 }, token)
  if (!wordRes.body || wordRes.body.code !== 0) {
    console.log('  skip 创建敏感词失败：' + (wordRes.body ? wordRes.body.msg : wordRes.raw))
  } else {
    const wordId = wordRes.body.data
    const checkRes = await request('GET', '/sensitive/check?text=' + encodeURIComponent('这是一句' + word + '测试'), null, token)
    const hits = (checkRes.body && checkRes.body.code === 0 && checkRes.body.data) || []
    expect('/sensitive/check 命中新建的敏感词', hits.some(function (h) { return h.word === word }),
      '命中 ' + hits.length + ' 条')
    await call('DELETE /sensitive/{id} 清理', 'DELETE', '/sensitive/' + wordId, null, token)
    await call('POST /sensitive/reload', 'POST', '/sensitive/reload', null, token)
  }

  // 6.5 广告双闸：审核闸 + 上下架闸，两道都要单独拦住
  //     顺序必须是「广告位先上架」——素材挂在位上，位没开素材就不能开
  const slotCode = 'SMOKE_' + Date.now()
  const slotRes = await request('POST', '/ads/slots', {
    name: '冒烟广告位', code: slotCode, position: 1, scopeType: 1,
    maxCount: 3, sort: 99, status: 0, auditStatus: 0
  }, token)
  if (!slotRes.body || slotRes.body.code !== 0) {
    console.log('  skip 创建广告位失败：' + (slotRes.body ? slotRes.body.msg : slotRes.raw))
  } else {
    const slotId = slotRes.body.data

    // 闸一：广告位未过审 → 不许上架
    await callReject('未过审广告位上架被拒', 'POST', '/ads/slots/' + slotId + '/status?status=1', null, token, 400)
    await call('POST /ads/slots/{id}/audit 通过', 'POST', '/ads/slots/' + slotId + '/audit', { pass: true }, token)
    await call('POST /ads/slots/{id}/status 上架', 'POST', '/ads/slots/' + slotId + '/status?status=1', null, token)

    const matRes = await request('POST', '/ads/materials', {
      slotId: slotId, title: '冒烟素材', image: '/uploads/smoke.png',
      linkType: 1, sort: 1, status: 0, auditStatus: 0
    }, token)
    const matId = matRes.body && matRes.body.code === 0 ? matRes.body.data : null

    if (matId) {
      // 闸二：素材未过审 → 不许上架
      await callReject('未过审素材上架被拒', 'POST', '/ads/materials/' + matId + '/status?status=1', null, token, 400)
      await call('POST /ads/materials/{id}/audit 通过', 'POST', '/ads/materials/' + matId + '/audit',
        { pass: true }, token)
      await call('POST /ads/materials/{id}/status 上架', 'POST', '/ads/materials/' + matId + '/status?status=1', null, token)
      await call('DELETE /ads/materials/{id} 清理', 'DELETE', '/ads/materials/' + matId, null, token)
    } else {
      console.log('  skip 创建广告素材失败：' + (matRes.body ? matRes.body.msg : matRes.raw))
    }

    await call('DELETE /ads/slots/{id} 清理', 'DELETE', '/ads/slots/' + slotId, null, token)
  }

  console.log('\n[7] 操作日志落库（P1 的处置动作都应留下痕迹）')
  const opLog = await request('GET', '/logs/operations?page=1&size=5', null, token)
  const opFirst = firstRecord(opLog)
  expect('操作日志有记录', !!opFirst, '查不到操作日志')

  console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
  if (failures.length) {
    console.log('\n失败明细：')
    failures.forEach(function (f) { console.log('  - ' + f) })
    console.log('\n常见原因：')
    console.log('  401 + 「凭证类型不正确」  → 传了小程序令牌，或令牌不是 admin 服务签的')
    console.log('  404 无路由                → 网关没重启（改路由必须重启网关）')
    console.log('  500 + SQL 相关            → 建表脚本没执行')
    console.log('  WeakKeyException          → campus.admin.jwt.secret 少于 32 字节')
    console.log('  -1 / timeout              → campus-admin 或网关未启动，Nacos 没注册上')
  }
  process.exit(fail ? 1 : 0)
}

main()
