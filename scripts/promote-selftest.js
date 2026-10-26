/**
 * 推广功能自检（看广告换排序）
 * 用法：node scripts/promote-selftest.js [http://127.0.0.1:8888]
 *
 * 覆盖的是「防刷」相关的几条硬约束，因为它们最容易在后续改动里被悄悄破坏：
 *   1. 凭证一次性（同一个 token 不能结算两次）
 *   2. 最短观看时长（没看完不能结算）
 *   3. 配额（单条每天上限，作废的凭证要释放配额）
 *   4. 归属（不能推广别人的内容）
 *   5. 排序（结算后内容表上的 promote_score 真的被改写）
 *
 * 需要后端已启动并开启 PROMOTE_MOCK_AD=true（本地联调模式，否则拿不到 mockAd，
 * 脚本仍会跑，只是不会真的等满观看时长）。
 */
const http = require('http')
const https = require('https')
const { URL } = require('url')

const SERVER = (process.argv[2] || 'http://127.0.0.1:8888').replace(/\/$/, '')
const BASE = SERVER + '/api'
/** 演示账号（t_user 9001 林小满），与 config.js 的 DEV_OPENID 一致 */
const DEV_OPENID = 'dev_openid_guizhou_campus_001'

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
        timeout: 20000
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

function assert(label, ok, extra) {
  if (ok) {
    pass++
    console.log('  ✓ ' + label)
  } else {
    fail++
    console.log('  ✗ ' + label + (extra ? '  → ' + extra : ''))
  }
}

function sleep(ms) {
  return new Promise(function (r) {
    setTimeout(r, ms)
  })
}

async function main() {
  console.log('\n=== 推广功能自检 ===\n  server: ' + SERVER + '\n')

  // ---- 登录 ----
  const login = await request('POST', '/auth/login', {
    code: 'test',
    platform: 'wx',
    devOpenid: DEV_OPENID
  })
  const token =
    login.body && login.body.code === 0 && login.body.data ? login.body.data.token : null
  if (!token) {
    console.log('  ✗ 登录失败，后续用例无法执行：' + (login.raw || '').slice(0, 200))
    fail++
    return
  }
  assert('登录成功', true)

  // ---- 未登录不能访问 ----
  const anon = await request('GET', '/promote/quota?targetType=GOODS&targetId=1')
  assert(
    '未登录访问 /promote/quota 被拒',
    anon.status === 401 || (anon.body && anon.body.code === 401),
    'status=' + anon.status + ' code=' + (anon.body && anon.body.code)
  )

  // ---- 找一条自己的在售闲置 ----
  const mine = await request('GET', '/goods/mine?status=1', null, token)
  const list = mine.body && mine.body.code === 0 && Array.isArray(mine.body.data) ? mine.body.data : []
  if (!list.length) {
    console.log('\n  ⚠ 演示账号没有在售闲置，跳过结算相关用例（先去发布一件再跑）')
    fail++
    return
  }
  const goods = list[0]
  const goodsId = goods.id
  assert('取到自己的在售闲置 id=' + goodsId, true)

  // ---- 归属：不存在的内容 ----
  const ghost = await request('GET', '/promote/quota?targetType=GOODS&targetId=999999999999999999', null, token)
  assert(
    '推广不存在的内容被拒',
    ghost.body && ghost.body.code !== 0,
    'code=' + (ghost.body && ghost.body.code)
  )

  // ---- 归属：不能推广别人的内容（用一个不属于自己的帖子 id 试，取不到就跳过）----
  const others = await request('GET', '/posts?page=1&size=20', null, token)
  const othersList =
    others.body && others.body.code === 0 && others.body.data ? others.body.data.records || [] : []
  const foreign = othersList.find(function (p) {
    return p && !p.mine
  })
  if (foreign) {
    const denied = await request(
      'GET',
      '/promote/quota?targetType=POST&targetId=' + foreign.id,
      null,
      token
    )
    assert(
      '推广别人的内容被拒（403）',
      denied.body && denied.body.code === 403,
      'code=' + (denied.body && denied.body.code)
    )
  } else {
    console.log('  · 列表里没有别人的帖子，跳过「推广他人内容」用例')
  }

  // ---- 配额基线 ----
  const q0 = await request('GET', '/promote/quota?targetType=GOODS&targetId=' + goodsId, null, token)
  assert(
    '拉取推广配额成功',
    q0.body && q0.body.code === 0,
    'code=' + (q0.body && q0.body.code) + ' msg=' + (q0.body && q0.body.msg)
  )
  const quota0 = q0.body && q0.body.data ? q0.body.data : null
  if (!quota0) return
  const minWatch = quota0.minWatchSeconds || 15
  const baseScore = quota0.baseScore || 100
  console.log(
    '  · 基线：当前分 ' +
      quota0.score +
      '，今日剩余 ' +
      quota0.remainTarget +
      '/' +
      quota0.dailyPerTarget +
      '，最短观看 ' +
      minWatch +
      's，mockAd=' +
      quota0.mockAd
  )

  // ---- 1. 没看完不能结算 ----
  const i1 = await request('POST', '/promote/issue', { targetType: 'GOODS', targetId: goodsId }, token)
  assert('签发凭证成功', i1.body && i1.body.code === 0, 'msg=' + (i1.body && i1.body.msg))
  const token1 = i1.body && i1.body.data ? i1.body.data.token : null
  const early = await request('POST', '/promote/complete', { token: token1 }, token)
  assert(
    '未看满时长结算被拒',
    early.body && early.body.code !== 0,
    'code=' + (early.body && early.body.code)
  )

  // ---- 2. 作废后配额要还回来 ----
  await request('POST', '/promote/cancel', { token: token1 }, token)
  const q1 = await request('GET', '/promote/quota?targetType=GOODS&targetId=' + goodsId, null, token)
  assert(
    '作废凭证后当日配额不变',
    q1.body && q1.body.data && q1.body.data.remainTarget === quota0.remainTarget,
    'remain ' +
      (q1.body && q1.body.data ? q1.body.data.remainTarget : '?') +
      ' vs ' +
      quota0.remainTarget
  )

  // ---- 3. 完整看一次 ----
  const i2 = await request('POST', '/promote/issue', { targetType: 'GOODS', targetId: goodsId }, token)
  const token2 = i2.body && i2.body.data ? i2.body.data.token : null
  console.log('  · 等待 ' + (minWatch + 1) + 's 模拟完整观看…')
  await sleep((minWatch + 1) * 1000)
  const done = await request('POST', '/promote/complete', { token: token2 }, token)
  assert(
    '看满时长后结算成功',
    done.body && done.body.code === 0,
    'code=' + (done.body && done.body.code) + ' msg=' + (done.body && done.body.msg)
  )
  const scoreAfter = done.body && done.body.data ? done.body.data.score : 0
  assert(
    '结算后分数增加（' + quota0.score + ' → ' + scoreAfter + '）',
    scoreAfter > quota0.score,
    'score=' + scoreAfter
  )

  // ---- 4. 凭证一次性 ----
  const again = await request('POST', '/promote/complete', { token: token2 }, token)
  assert(
    '同一凭证重复结算被拒',
    again.body && again.body.code !== 0,
    'code=' + (again.body && again.body.code)
  )

  // ---- 5. 配额真的扣了 ----
  const q2 = await request('GET', '/promote/quota?targetType=GOODS&targetId=' + goodsId, null, token)
  assert(
    '结算后当日配额 -1',
    q2.body && q2.body.data && q2.body.data.remainTarget === quota0.remainTarget - 1,
    'remain ' + (q2.body && q2.body.data ? q2.body.data.remainTarget : '?')
  )
  assert(
    '配额回显的分数与结算一致',
    q2.body && q2.body.data && q2.body.data.score === scoreAfter,
    'quota.score=' + (q2.body && q2.body.data ? q2.body.data.score : '?')
  )

  // ---- 6. 商品表上的 promote_score 被改写 ----
  const detail = await request('GET', '/goods/' + goodsId, null, token)
  const promoted = detail.body && detail.body.code === 0 ? detail.body.data.promoteScore : -1
  assert(
    '商品详情 promote_score 已同步（' + promoted + '）',
    promoted === scoreAfter,
    'promoteScore=' + promoted + ' 期望=' + scoreAfter
  )

  // ---- 7. 列表默认排序把推广的排前面 ----
  const page = await request('GET', '/goods?page=1&size=10&sort=latest', null, token)
  const recs = page.body && page.body.data ? page.body.data.records || [] : []
  let ordered = true
  for (let i = 1; i < recs.length; i++) {
    const a = Number(recs[i - 1].promoteScore || 0)
    const b = Number(recs[i].promoteScore || 0)
    if (a < b) {
      ordered = false
      break
    }
  }
  assert('闲置列表按推广分降序', recs.length > 0 && ordered, '共 ' + recs.length + ' 条')

  console.log('\n=== ' + pass + ' 通过 / ' + fail + ' 失败 ===\n')
  if (fail > 0) process.exitCode = 1
}

main().catch(function (e) {
  console.error('自检异常：', e)
  process.exitCode = 1
})
