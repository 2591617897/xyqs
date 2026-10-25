/**
 * 首页功能区回归测试（scripts/home-zones-selftest.js）
 * ------------------------------------------------------------------
 * 直接跑 pages/index/index.js 的真实逻辑，而不是复制一遍：
 *   1. 用 require.cache 注入假的 utils/request，转发到 mock 数据层；
 *   2. 捕获 Page() 注册的配置，调用 onLoad/onShow，驱动真实的 loadData；
 *   3. 断言 setData 之后的 zones / entries 结构。
 *
 * 目的：utils/zones.js 的纯函数已被 mock-selftest 覆盖，
 * 这里覆盖的是「首页把 6 个接口的返回拼成 zones」这段接线，
 * 最容易出的错是 res 下标错位（改一个请求顺序就静默串区）。
 */
const path = require('path')

/* ---------- 宿主环境桩 ---------- */
const store = {}
const mp = {
  getStorageSync: (k) => (k in store ? store[k] : ''),
  setStorageSync: (k, v) => { store[k] = JSON.parse(JSON.stringify(v)) },
  removeStorageSync: (k) => { delete store[k] },
  showToast: () => {},
  showLoading: () => {},
  hideLoading: () => {},
  navigateTo: () => {},
  switchTab: () => {},
  showModal: (opts) => {
    // 真实小程序弹窗要用户点确定；测试里自动确认，否则 ensureLogin 的 Promise 永不落地
    if (opts && opts.success) opts.success({ confirm: true, cancel: false })
  },
  login: (opts) => {
    // 宿主登录能力：直接给一个 code，交给 mock 的 /auth/login 换用户
    if (opts && opts.success) opts.success({ code: 'mock_code' })
  },
  stopPullDownRefresh: () => {},
  setNavigationBarTitle: () => {},
  getMenuButtonBoundingClientRect: () => ({ left: 300, top: 44, width: 87, height: 32 })
}
global.qq = mp
global.wx = mp
global.getApp = () => ({ globalData: {} })

/* ---------- 假 request：转发到 mock 数据层 ---------- */
const mock = require(path.join(__dirname, '../miniprogram/utils/mock.js'))

function unwrap(r) {
  if (!r || r.code !== 0) {
    const e = new Error((r && r.msg) || '请求失败')
    e.code = (r && r.code) || 500
    throw e
  }
  return r.data
}
const fakeHttp = {
  get: (url, params) => Promise.resolve(unwrap(mock.handle('GET', url, params || {}))),
  post: (url, data) => Promise.resolve(unwrap(mock.handle('POST', url, data || {}))),
  put: (url, data) => Promise.resolve(unwrap(mock.handle('PUT', url, data || {}))),
  del: (url, data) => Promise.resolve(unwrap(mock.handle('DELETE', url, data || {}))),
  getToken: () => 'mock-token',
  setToken: () => {},
  cleanLogin: () => {}
}
const reqPath = require.resolve(path.join(__dirname, '../miniprogram/utils/request.js'))
require.cache[reqPath] = {
  id: reqPath,
  filename: reqPath,
  loaded: true,
  exports: fakeHttp
}

/* ---------- 捕获 Page() ---------- */
let pageCfg = null
global.Page = (cfg) => { pageCfg = cfg }

// 首页还有 auth / scope 的间接依赖：auth.ensureLogin 走 request（已注入假实现）
const indexPath = require.resolve(path.join(__dirname, '../miniprogram/pages/index/index.js'))
require(indexPath)

let pass = 0
let fail = 0
function check(name, cond, extra) {
  if (cond) { pass++; console.log('  ok   ' + name) }
  else { fail++; console.log('  FAIL ' + name + (extra !== undefined ? ' → ' + JSON.stringify(extra) : '')) }
}

/** 同步跑完一组 promise */
function run(promises) {
  return Promise.all(promises.map((p) => p.then((v) => ({ ok: true, v }), (e) => ({ ok: false, e }))))
}

async function main() {
  // mock 层：先登录，让 ensureLogin 有用户
  mock.handle('POST', '/auth/login', { code: 'x' })
  // 预置「已登录」的缓存用户：ensureLogin 命中缓存就直接放行，不弹登录确认框
  mp.setStorageSync('user', { id: 9001, nickname: '林小满', verifyStatus: 2, schoolId: 1005 })

  const page = Object.assign({}, pageCfg)
  page.data = JSON.parse(JSON.stringify(pageCfg.data))
  /**
   * 模拟小程序 setData 的路径写法，支持 a[0].b[1].c 这类嵌套下标。
   * 路径段拆成 ['zones', 0, 'items', 0, 'liked'] 逐层走。
   */
  function applyPath(target, pathStr, value) {
    const segs = []
    pathStr.replace(/([^.[]+)|\[(\d+)\]/g, function (_, name, idx) {
      segs.push(idx !== undefined ? Number(idx) : name)
      return ''
    })
    let cur = target
    for (let i = 0; i < segs.length - 1; i++) {
      const s = segs[i]
      if (cur[s] === undefined || cur[s] === null) cur[s] = typeof segs[i + 1] === 'number' ? [] : {}
      cur = cur[s]
    }
    cur[segs[segs.length - 1]] = value
  }
  page.setData = function (patch) {
    Object.keys(patch).forEach((k) => {
      const m = k.match(/^([^\[]+)(.*)$/)
      if (!m) { page.data[k] = patch[k]; return }
      applyPath(page.data, m[1] + m[2], patch[k])
    })
  }

  console.log('\n[1] 首页加载真实 loadData')
  page.onLoad()
  page.loadData()
  await new Promise((r) => setTimeout(r, 300))

  const d = page.data
  check('加载完成（loading=false）', d.loading === false, d.loading)
  check('没有加载错误', d.loadError === false, d.errorMsg)
  check('生成金刚区入口', d.entries.length === 8, d.entries.length)
  check('金刚区首项是「我想说」', d.entries[0] && d.entries[0].title === '我想说')
  check('金刚区没有「学习交流」', !d.entries.some((e) => e.title === '学习交流'))
  check('生成内容功能区', d.zones.length === 5, d.zones.map((z) => z.key))
  check('区顺序正确', d.zones.map((z) => z.key).join(',') === 'hot,wall,goods,notice,campus')

  console.log('\n[2] 各区内容来自正确的接口（防止 res 下标串位）')
  const byKey = {}
  d.zones.forEach((z) => { byKey[z.key] = z })
  check('热区 1 条帖子', byKey.hot.items.length === 1 && byKey.hot.items[0].kind === 'post')
  check('热区有作者与频道名', !!byKey.hot.items[0].authorName && !!byKey.hot.items[0].badge)
  check('我想说区是表白墙数据', byKey.wall.items.length === 6 && byKey.wall.items[0].kind === 'wall')
  check('我想说区带匿名昵称', !!byKey.wall.items[0].anonName && !!byKey.wall.items[0].avatarClass)
  check('闲置区是商品数据', byKey.goods.items.length === 6 && byKey.goods.items[0].kind === 'goods')
  check('闲置区带价格', byKey.goods.items[0].priceText !== undefined && byKey.goods.items[0].priceText !== '')
  check('公告区只含 2001 频道', byKey.notice.items.every((it) => it.badge === '校园公告'))
  check('生活区不含公告频道', byKey.campus.items.every((it) => it.badge !== '校园公告'))
  check('生活区有多个频道', new Set(byKey.campus.items.map((it) => it.badge)).size >= 1)
  check('拿到闲置分类', d.categories.length > 0, d.categories.length)

  console.log('\n[3] 功能区点赞：按 kind 走不同接口')
  const wallItem = byKey.wall.items[0]
  // 种子数据里 7001 可能已经点过赞，先记初始态，按「取反」断言
  const likedBefore = !!wallItem.liked
  const countBefore = wallItem.likeCount || 0
  page.toggleZoneLike({ currentTarget: { dataset: { zi: 1, ii: 0 } } })
  await new Promise((r) => setTimeout(r, 200))
  const after1 = d.zones[1].items[0]
  check('点赞状态取反', after1.liked === !likedBefore, after1.liked)
  check('点赞数 ' + (likedBefore ? '-1' : '+1'), after1.likeCount === countBefore + (likedBefore ? -1 : 1), after1.likeCount)
  check('点赞文案同步', after1.likeText === String(after1.likeCount), after1.likeText)
  page.toggleZoneLike({ currentTarget: { dataset: { zi: 1, ii: 0 } } })
  await new Promise((r) => setTimeout(r, 200))
  const after2 = d.zones[1].items[0]
  check('再点一次恢复原状', after2.liked === likedBefore && after2.likeCount === countBefore, after2)

  console.log('\n[4] 跳转路由')
  const jumps = []
  mp.switchTab = (o) => jumps.push(['switchTab', o.url])
  mp.navigateTo = (o) => jumps.push(['navigateTo', o.url])
  page.goEntry({ currentTarget: { dataset: { url: '/pages/wall/index' } } })
  page.goEntry({ currentTarget: { dataset: { url: '/pages/post/list?channelId=2001' } } })
  page.goZoneDetail({ currentTarget: { dataset: { kind: 'wall', id: '7001' } } })
  page.goZoneDetail({ currentTarget: { dataset: { kind: 'goods', id: '8001' } } })
  page.goZoneDetail({ currentTarget: { dataset: { kind: 'post', id: '6001' } } })
  check('tab 页用 switchTab', jumps[0] && jumps[0][0] === 'switchTab' && jumps[0][1] === '/pages/wall/index', jumps[0])
  check('非 tab 页用 navigateTo 且带参', jumps[1] && jumps[1][0] === 'navigateTo' && jumps[1][1] === '/pages/post/list?channelId=2001', jumps[1])
  check('表白墙跳详情', jumps[2] && jumps[2][1] === '/pages/wall/detail?id=7001', jumps[2])
  check('闲置跳详情', jumps[3] && jumps[3][1] === '/pages/goods/detail?id=8001', jumps[3])
  check('帖子跳详情', jumps[4] && jumps[4][1] === '/pages/post/detail?id=6001', jumps[4])

  console.log('\n[5] 无数据时首页不空白报错')
  const origGet = fakeHttp.get
  fakeHttp.get = (url) => {
    if (String(url).indexOf('/posts') === 0 || String(url).indexOf('/wall') === 0 || String(url).indexOf('/goods') === 0) {
      return Promise.reject({ code: 500, msg: '模拟故障' })
    }
    return origGet(url)
  }
  page.loadData(true)
  await new Promise((r) => setTimeout(r, 300))
  check('全部失败时 loadError=true', page.data.loadError === true)
  check('有可读的错误提示', !!page.data.errorMsg, page.data.errorMsg)
  check('功能区为空（不渲染空模块）', page.data.zones.length === 0)
  fakeHttp.get = origGet

  console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
  process.exit(fail ? 1 : 0)
}

main().catch((e) => {
  console.error('测试执行异常：', e)
  process.exit(1)
})
