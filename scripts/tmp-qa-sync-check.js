// 临时 QA 脚本：模拟小程序宿主，端到端验证「首页学校胶囊 / 认证学籍一致性」修复。
// 跑完即删，不入库。
const path = require('path')

/* ---------------- 宿主模拟 ---------------- */
const store = {}
const calls = []          // 记录所有请求 {method, url}
let route = {}            // 'METHOD /url' -> {statusCode, data} | 'FAIL' | 'NETFAIL'
let delay = 1

function resetHost() {
  for (const k of Object.keys(store)) delete store[k]
  calls.length = 0
  route = {}
  app.globalData = { token: '', user: null, schoolId: null, goodsMode: '' }
}

/** 真实请求带 BASE_URL 前缀，这里统一还原成 '/xxx' 便于比对 */
function shortUrl(u) {
  return String(u).replace(/^https?:\/\/[^/]+/, '').replace(/^\/api/, '')
}

function respond(opts) {
  const key = opts.method + ' ' + shortUrl(opts.url)
  const r = route[key]
  if (r === 'NETFAIL') {
    setTimeout(function () { opts.fail({ errMsg: 'request:fail net error' }) }, delay)
    return
  }
  const body = r === undefined
    ? { code: 0, data: (opts.url.indexOf('/posts') >= 0 || opts.url.indexOf('/goods') >= 0) ? { records: [], total: 0 } : [] }
    : r
  setTimeout(function () {
    opts.success({ statusCode: body.statusCode || 200, data: body })
  }, delay)
}

global.wx = {
  getStorageSync: (k) => (k in store ? store[k] : ''),
  setStorageSync: (k, v) => { store[k] = JSON.parse(JSON.stringify(v)) },
  removeStorageSync: (k) => { delete store[k] },
  request: function (opts) {
    calls.push({ method: opts.method, url: shortUrl(opts.url) })
    respond(opts)
  },
  showToast: () => {},
  showLoading: () => {},
  hideLoading: () => {},
  showModal: () => {},
  navigateTo: () => {},
  navigateBack: () => {},
  switchTab: () => {},
  stopPullDownRefresh: () => {},
  setNavigationBarTitle: () => {},
  login: (o) => o.success({ code: 'mock_code' }),
  chooseImage: () => {},
  previewImage: () => {},
  uploadFile: () => {}
}

const app = { globalData: { token: '', user: null, schoolId: null, goodsMode: '' } }
global.getApp = () => app

let captured = null
global.Page = function (cfg) { captured = cfg }
global.App = function () {}

/* ---------------- 页面实例化 ---------------- */
function makePage(file) {
  captured = null
  const full = path.join(__dirname, '../miniprogram', file)
  delete require.cache[full]          // 每个用例拿一份干净的 Page 配置
  require(full)
  const cfg = captured
  const p = Object.assign({}, cfg)
  p.data = JSON.parse(JSON.stringify(cfg.data))
  p.setData = function (patch) { Object.assign(this.data, patch) }
  return p
}

/* ---------------- 断言 ---------------- */
let pass = 0
let fail = 0
function check(name, cond, extra) {
  if (cond) { pass++; console.log('  ok   ' + name) } else {
    fail++
    console.log('  FAIL ' + name + (extra !== undefined ? ' → ' + JSON.stringify(extra) : ''))
  }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const indexFile = 'pages/index/index.js'
const verifyFile = 'pages/user/verify.js'
const userFile = 'pages/user/index.js'
const profileFile = 'pages/user/profile.js'

async function main() {
  console.log('\n[1] 已认证用户进首页 → 胶囊显示「北京大学」')
  resetHost()
  {
    // 登录后缓存只有 token + 简要 user（后端 login 不返回 schoolName，正是 bug 现场）
    store.token = 'tk-1'
    store.user = { id: 1, nickname: '小明', avatar: '', schoolId: 2, verifyStatus: 2 }
    app.globalData.token = 'tk-1'
    app.globalData.user = store.user
    route['GET /user/me'] = {
      code: 0,
      data: { id: 1, nickname: '小明', avatar: '', schoolId: 2, schoolName: '北京大学', verifyStatus: 2 }
    }
    const p = makePage(indexFile)
    p.onShow()
    check('onShow 首帧先用缓存兜底（不是空白）', p.data.schoolName === '选择学校', p.data.schoolName)
    await sleep(60)
    check('/user/me 已发起', calls.filter((c) => c.url === '/user/me').length === 1, calls)
    check('胶囊显示「北京大学」', p.data.schoolName === '北京大学', p.data.schoolName)
    check('globalData.user.schoolName 已写入', app.globalData.user.schoolName === '北京大学', app.globalData.user)
    check('storage.user.schoolName 已写入', store.user.schoolName === '北京大学', store.user)
    check('原有字段未被清掉', app.globalData.user.nickname === '小明' && app.globalData.user.id === 1, app.globalData.user)
  }

  console.log('\n[2] 未登录进首页 → 「选择学校」且不发 /user/me')
  resetHost()
  {
    const p = makePage(indexFile)
    p.onShow()
    await sleep(60)
    check('胶囊显示「选择学校」', p.data.schoolName === '选择学校', p.data.schoolName)
    check('没有发起 /user/me', calls.filter((c) => c.url === '/user/me').length === 0, calls)
    check('页面其它数据照常加载', Array.isArray(p.data.feed), p.data.feed)
  }

  console.log('\n[3] /user/me 失败 → 不报错且保留缓存学校名')
  resetHost()
  {
    store.token = 'tk-2'
    store.user = { id: 2, nickname: '小红', schoolName: '复旦大学', verifyStatus: 2 }
    app.globalData.token = 'tk-2'
    app.globalData.user = store.user
    route['GET /user/me'] = 'NETFAIL'
    const p = makePage(indexFile)
    p.onShow()
    await sleep(60)
    check('保留缓存学校名「复旦大学」', p.data.schoolName === '复旦大学', p.data.schoolName)
    check('缓存未被清空', app.globalData.user && app.globalData.user.schoolName === '复旦大学', app.globalData.user)
    check('未发生未捕获异常', true)
  }

  console.log('\n[3b] /user/me 返回 401 → 静默失败，不弹错、不留死循环')
  resetHost()
  {
    store.token = 'tk-expired'
    store.user = { id: 3, schoolName: '南京大学', verifyStatus: 2 }
    app.globalData.token = 'tk-expired'
    app.globalData.user = store.user
    route['GET /user/me'] = { statusCode: 401, code: 401, msg: '未登录' }
    const p = makePage(indexFile)
    p.onShow()
    await sleep(60)
    check('401 后无死循环（/user/me 只发 1 次）', calls.filter((c) => c.url === '/user/me').length === 1, calls)
    check('401 后 token 被清理', !store.token, store.token)
    check('401 后页面仍保留缓存学校名（当前会话）', p.data.schoolName === '南京大学', p.data.schoolName)
  }

  console.log('\n[4] 认证提交成功 → 缓存写入「北京大学」')
  resetHost()
  {
    store.token = 'tk-3'
    store.user = { id: 4, nickname: '小刚', verifyStatus: 0 }
    app.globalData.token = 'tk-3'
    app.globalData.user = store.user
    route['POST /user/verify'] = { code: 0, data: { verifyStatus: 2, token: 'tk-3-new', message: '认证成功' } }
    route['GET /user/me'] = {
      code: 0,
      data: { id: 4, nickname: '小刚', avatar: '', schoolId: 2, schoolName: '北京大学', verifyStatus: 2 }
    }
    const v = makePage(verifyFile)
    v.data.schoolId = 2
    v.data.studentNo = '20220101'
    v.data.realName = '小刚'
    v.data.verifyImg = '/uploads/a.png'
    v.submit()
    await sleep(80)
    check('globalData.user.schoolName = 北京大学', app.globalData.user.schoolName === '北京大学', app.globalData.user)
    check('storage.user.schoolName = 北京大学', store.user.schoolName === '北京大学', store.user)
    check('verifyStatus 已同步为 2', app.globalData.user.verifyStatus === 2, app.globalData.user)
    check('新 token 已落盘', store.token === 'tk-3-new', store.token)
    check('提交按钮复位（submitting=false）', v.data.submitting === false, v.data.submitting)

    // 认证完回首页：直接走 onShow，胶囊应立即是「北京大学」
    const p = makePage(indexFile)
    p.onShow()
    await sleep(60)
    check('认证后回首页胶囊显示「北京大学」', p.data.schoolName === '北京大学', p.data.schoolName)
  }

  console.log('\n[5] updateUser 合并语义')
  resetHost()
  {
    const auth = require(path.join(__dirname, '../miniprogram/utils/auth.js'))
    app.globalData.user = { id: 9, nickname: '小鱼', schoolName: '北京大学', verifyStatus: 2 }
    store.user = { id: 9, nickname: '小鱼', schoolName: '北京大学', verifyStatus: 2 }

    const a = auth.updateUser({ verifyStatus: 1 })
    check('只传部分字段：schoolName 保留', a.schoolName === '北京大学', a)
    check('只传部分字段：id/nickname 保留', a.id === 9 && a.nickname === '小鱼', a)
    check('新字段被写入', a.verifyStatus === 1, a)
    check('返回合并后的完整对象（非入参）', a !== undefined && Object.keys(a).length >= 4, a)

    const b = auth.updateUser({ schoolName: undefined, nickname: undefined })
    console.log('       [观察] updateUser({schoolName: undefined}) 后 schoolName =',
      JSON.stringify(b.schoolName), '（undefined 会覆盖：调用方必须用 || \'\' 兜底）')
    auth.updateUser({ schoolName: '北京大学' })

    resetHost()
    app.globalData.user = null
    delete app.globalData.user
    const c = auth.updateUser({ id: 5, schoolName: '清华大学' })
    check('globalData 为空时回落到 storage/空对象', c && c.schoolName === '清华大学', c)
    check('空缓存时 storage 也被写入', store.user && store.user.schoolName === '清华大学', store.user)

    resetHost()
    app.globalData.user = null
    const d = auth.updateUser(null)
    check('入参为 null 不抛异常', !!d, d)
  }

  console.log('\n[7] /user/me 成功但返回体不含 schoolName（未认证 / 老后端）')
  resetHost()
  {
    store.token = 'tk-7'
    store.user = { id: 7, schoolName: '北京大学', verifyStatus: 2 }
    app.globalData.token = 'tk-7'
    app.globalData.user = store.user
    route['GET /user/me'] = { code: 0, data: { id: 7, nickname: '小旧', verifyStatus: 0 } }
    const p = makePage(indexFile)
    p.onShow()
    await sleep(60)
    console.log('       [观察] 胶囊 =', JSON.stringify(p.data.schoolName),
      '| 缓存 user =', JSON.stringify(app.globalData.user))
    check('未认证（无 schoolName）时胶囊回落「选择学校」', p.data.schoolName === '选择学校', p.data.schoolName)
  }

  console.log('\n[8] 我的 / 个人资料页：/user/me 后缓存补齐 schoolName')
  resetHost()
  {
    store.token = 'tk-8'
    store.user = { id: 8, nickname: '小美', verifyStatus: 2 }
    app.globalData.token = 'tk-8'
    app.globalData.user = store.user
    route['GET /user/me'] = {
      code: 0,
      data: { id: 8, nickname: '小美', avatar: '', gender: 0, phone: '', bio: '', schoolId: 3, schoolName: '浙江大学', verifyStatus: 2 }
    }
    const u = makePage(userFile)
    u.onShow()
    await sleep(80)
    check('我的页 loadMe 写入 schoolName', app.globalData.user.schoolName === '浙江大学', app.globalData.user)
    check('storage 同步', store.user.schoolName === '浙江大学', store.user)

    const pf = makePage(profileFile)
    pf.onShow()
    await sleep(80)
    check('资料页 applyMe 写入 schoolName', app.globalData.user.schoolName === '浙江大学', app.globalData.user)
    check('资料页表单已填充', pf.data.nickname === '小美', pf.data.nickname)
  }

  console.log('\n[9] 个人资料页遇 401 → 不崩溃且置为未登录')
  resetHost()
  {
    store.token = 'tk-exp'
    app.globalData.token = 'tk-exp'
    app.globalData.user = { id: 8 }
    route['GET /user/me'] = { statusCode: 401, code: 401, msg: '未登录' }
    const pf = makePage(profileFile)
    pf.onShow()
    await sleep(80)
    check('401 → logged=false', pf.data.logged === false, pf.data.logged)
    check('401 → user=null', pf.data.user === null, pf.data.user)
  }

  console.log('\n[10] 连续两次 onShow（切 tab 回首页）的请求量')
  resetHost()
  {
    store.token = 'tk-10'
    app.globalData.token = 'tk-10'
    app.globalData.user = { id: 10 }
    route['GET /user/me'] = { code: 0, data: { id: 10, schoolName: '北京大学', verifyStatus: 2 } }
    const p = makePage(indexFile)
    p.onShow()
    p.onShow()
    await sleep(80)
    const n = calls.filter((c) => c.url === '/user/me').length
    check('每次 onShow 各发 1 次 /user/me（无重复叠加）', n === 2, n)
    check('胶囊仍为「北京大学」', p.data.schoolName === '北京大学', p.data.schoolName)
  }

  console.log('\n[6] 其它页面的 updateUser 是否补传 schoolName（源码级）')
  {
    const fs = require('fs')
    const files = [userFile, profileFile, verifyFile, indexFile]
    files.forEach(function (f) {
      const src = fs.readFileSync(path.join(__dirname, '../miniprogram', f), 'utf8')
      const okSchool = /schoolName:/.test(src)
      check(f + ' 含 schoolName 字段传递', okSchool)
    })
  }

  console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
  process.exit(fail ? 1 : 0)
}

main()
