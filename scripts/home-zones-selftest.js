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
const zoneMod = require(path.join(__dirname, '../miniprogram/utils/zones.js'))

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
  // 金刚区已独立成「功能区」页，首页不再持有 entries：入口生成改由 zones.js 直接断言
  const entryList = zoneMod.buildEntries(mock.handle('GET', '/channels', {}).data)
  // 分类整理后只保留 3 个上线频道（2004/2005 已软下线）；
  // 2026-10-28 起固定入口增加「需求广场」（求物/求帮忙），入口 4+3=7
  check('功能入口 7 个（4 固定 + 3 频道）', entryList.length === 7, entryList.length)
  check('功能入口首项是「树洞闲谈」', entryList[0] && entryList[0].title === '树洞闲谈')
  check('功能入口含「需求广场」', entryList.some((e) => e.key === 'demand'))
  check('需求广场落点指向 demand 分包',
    entryList.some((e) => e.key === 'demand' && e.url === '/subpackages/demand/list'))
  check('功能入口没有「学习交流」', !entryList.some((e) => e.title === '学习交流'))
  check('首页不再渲染金刚区', d.entries === undefined)
  // 2026-10-28：首页只留「今日最热」一个独立区，树洞/闲置/公告/生活全部并进广场混合流
  check('内容区只剩「今日最热」', d.zones.length === 1 && d.zones[0].key === 'hot', d.zones.map((z) => z.key))

  console.log('\n[2] 校园广场：分类 tab + 混合时间流')
  check('默认停在「全部」', d.plaza.activeTab === 'all' && d.plazaTab === 'all', d.plaza.activeTab)
  check('tab = 4 固定 + 3 频道', d.plaza.tabs.length === 7, d.plaza.tabs.map((t) => t.name))
  check('第一个 tab 是「全部」', d.plaza.tabs[0].key === 'all' && d.plaza.tabs[0].name === '全部')
  check('固定 tab 顺序 全部/树洞/闲置/需求',
    d.plaza.tabs.slice(0, 4).map((t) => t.key).join(',') === 'all,wall,goods,demand',
    d.plaza.tabs.slice(0, 4).map((t) => t.key))
  check('频道 tab 用 ch- 前缀（避开固定 key）', d.plaza.tabs.slice(4).every((t) => t.key.indexOf('ch-') === 0))
  check('广场有卡片', d.plaza.items.length > 0, d.plaza.items.length)
  check('卡片数不超过上限', d.plaza.items.length <= zoneMod.PLAZA_LIMIT, d.plaza.items.length)

  // 「全部」流混排四类：帖子 / 树洞 / 闲置 / 需求
  const kinds = {}
  d.plaza.items.forEach((it) => { kinds[it.kind] = (kinds[it.kind] || 0) + 1 })
  check('广场含帖子卡', !!kinds.post, kinds)
  check('广场含树洞卡', !!kinds.wall, kinds)
  check('广场含闲置卡', !!kinds.goods, kinds)
  check('广场含需求卡', !!kinds.demand, kinds)

  // 排序：推广帖置顶，其余按发布时间倒序
  const normal = d.plaza.items.filter((it) => !it.promoted)
  const orderOk = normal.every((it, i) => i === 0 || normal[i - 1].ts >= it.ts)
  check('非推广卡按时间倒序', orderOk, normal.map((it) => it.ts))
  const firstNormal = d.plaza.items.findIndex((it) => !it.promoted)
  check('推广卡全部置顶', firstNormal < 0 || d.plaza.items.slice(firstNormal).every((it) => !it.promoted),
    d.plaza.items.map((it) => (it.promoted ? 1 : 0)))

  console.log('\n[3] 广场卡片字段来自正确接口（防止 res 下标串位）')
  const wallCard = d.plaza.items.filter((it) => it.kind === 'wall')[0]
  check('树洞卡带匿名昵称', !!wallCard && !!wallCard.anonName && !!wallCard.avatarClass)
  const goodsCard = d.plaza.items.filter((it) => it.kind === 'goods')[0]
  check('闲置卡带价格', !!goodsCard && goodsCard.priceText !== undefined && goodsCard.priceText !== '')
  const demandCard = d.plaza.items.filter((it) => it.kind === 'demand')[0]
  check('需求卡带预算与回应数', !!demandCard && !!demandCard.budgetText && demandCard.replyText !== undefined)
  check('需求卡没有点赞（需求不做社交互动）', !!demandCard && demandCard.likeText === '')
  check('热区 1 条帖子', d.zones[0].items.length === 1 && d.zones[0].items[0].kind === 'post')
  check('热区有作者与频道名', !!d.zones[0].items[0].authorName && !!d.zones[0].items[0].badge)
  check('拿到闲置分类', d.categories.length > 0, d.categories.length)

  console.log('\n[4] 广场分类切换')
  const wallOnly = page.switchPlazaTab({ currentTarget: { dataset: { key: 'wall' } } })
  await new Promise((r) => setTimeout(r, 100))
  check('切到「树洞」只用树洞卡', page.data.plaza.items.every((it) => it.kind === 'wall'), page.data.plaza.items.length)
  check('切换后 plazaTab 同步', page.data.plazaTab === 'wall')
  page.switchPlazaTab({ currentTarget: { dataset: { key: 'demand' } } })
  await new Promise((r) => setTimeout(r, 100))
  check('切到「需求」只有需求卡', page.data.plaza.items.length > 0 && page.data.plaza.items.every((it) => it.kind === 'demand'))
  page.switchPlazaTab({ currentTarget: { dataset: { key: 'all' } } })
  await new Promise((r) => setTimeout(r, 100))
  check('切回「全部」恢复混合流', page.data.plaza.items.length > 0 && page.data.plazaTab === 'all')

  console.log('\n[5] 广场点赞：按 kind 走不同接口')
  const wallIdx = page.data.plaza.items.findIndex((it) => it.kind === 'wall')
  const wItem = page.data.plaza.items[wallIdx]
  const likedBefore = !!wItem.liked
  const countBefore = wItem.likeCount || 0
  page.togglePlazaLike({ currentTarget: { dataset: { idx: wallIdx } } })
  await new Promise((r) => setTimeout(r, 200))
  const after1 = page.data.plaza.items[wallIdx]
  check('点赞状态取反', after1.liked === !likedBefore, after1.liked)
  check('点赞数 ' + (likedBefore ? '-1' : '+1'), after1.likeCount === countBefore + (likedBefore ? -1 : 1), after1.likeCount)
  check('点赞文案同步', after1.likeText === String(after1.likeCount), after1.likeText)
  page.togglePlazaLike({ currentTarget: { dataset: { idx: wallIdx } } })
  await new Promise((r) => setTimeout(r, 200))
  const after2 = page.data.plaza.items[wallIdx]
  check('再点一次恢复原状', after2.liked === likedBefore && after2.likeCount === countBefore, after2)
  // 需求卡不该被点赞改写
  const dIdx = page.data.plaza.items.findIndex((it) => it.kind === 'demand')
  if (dIdx >= 0) {
    const dBefore = JSON.parse(JSON.stringify(page.data.plaza.items[dIdx]))
    page.togglePlazaLike({ currentTarget: { dataset: { idx: dIdx } } })
    await new Promise((r) => setTimeout(r, 150))
    check('需求卡点赞被忽略', page.data.plaza.items[dIdx].likeCount === dBefore.likeCount)
  }

  console.log('\n[6] 跳转路由')
  const jumps = []
  mp.switchTab = (o) => jumps.push(['switchTab', o.url])
  mp.navigateTo = (o) => jumps.push(['navigateTo', o.url])
  // 金刚区的 goEntry 已随页面搬走，这里改测功能区右上角「更多」与广场落点
  page.goZoneMore({ currentTarget: { dataset: { url: '/pages/wall/index' } } })
  page.goZoneMore({ currentTarget: { dataset: { url: '/pages/post/list?channelId=2001' } } })
  page.goZoneDetail({ currentTarget: { dataset: { kind: 'wall', id: '7001' } } })
  page.goZoneDetail({ currentTarget: { dataset: { kind: 'goods', id: '8001' } } })
  page.goZoneDetail({ currentTarget: { dataset: { kind: 'post', id: '6001' } } })
  // 广场卡片四种落点：需求必须落到 demand 分包（漏了会静默进帖子详情 → 404）
  page.goPlazaDetail({ currentTarget: { dataset: { kind: 'demand', id: '9001' } } })
  page.goPlazaDetail({ currentTarget: { dataset: { kind: 'wall', id: '7001' } } })
  page.goPlazaDetail({ currentTarget: { dataset: { kind: 'goods', id: '8001' } } })
  page.goPlazaDetail({ currentTarget: { dataset: { kind: 'post', id: '6001' } } })
  // 表白墙不再是 tab 页（底栏已换成 功能区/私信）→ navigateTo
  check('非 tab 页用 navigateTo', jumps[0] && jumps[0][0] === 'navigateTo' && jumps[0][1] === '/pages/wall/index', jumps[0])
  check('非 tab 页用 navigateTo 且带参', jumps[1] && jumps[1][0] === 'navigateTo' && jumps[1][1] === '/pages/post/list?channelId=2001', jumps[1])
  check('表白墙跳详情', jumps[2] && jumps[2][1] === '/pages/wall/detail?id=7001', jumps[2])
  check('闲置跳详情', jumps[3] && jumps[3][1] === '/pages/goods/detail?id=8001', jumps[3])
  check('帖子跳详情', jumps[4] && jumps[4][1] === '/pages/post/detail?id=6001', jumps[4])
  check('广场·需求跳 demand 分包', jumps[5] && jumps[5][1] === '/subpackages/demand/detail?id=9001', jumps[5])
  check('广场·树洞跳表白墙详情', jumps[6] && jumps[6][1] === '/pages/wall/detail?id=7001', jumps[6])
  check('广场·闲置跳闲置详情', jumps[7] && jumps[7][1] === '/pages/goods/detail?id=8001', jumps[7])
  check('广场·帖子跳帖子详情', jumps[8] && jumps[8][1] === '/pages/post/detail?id=6001', jumps[8])

  console.log('\n[7] 无数据时首页不空白报错')
  const origGet = fakeHttp.get
  fakeHttp.get = (url) => {
    const u = String(url)
    // 四个内容源全挂（含 /demands：它也是 contentOk 的判定源，漏了这里测不出整页失败）
    if (u.indexOf('/posts') === 0 || u.indexOf('/wall') === 0 ||
        u.indexOf('/goods') === 0 || u.indexOf('/demands') === 0) {
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

  console.log('\n[8] 功能区页（底栏第 2 项，新增页）')
  let zoneCfg = null
  global.Page = (cfg) => { zoneCfg = cfg }
  const zonePath = require.resolve(path.join(__dirname, '../miniprogram/pages/zone/index.js'))
  delete require.cache[zonePath]
  require(zonePath)
  const zpage = Object.assign({}, zoneCfg)
  zpage.data = JSON.parse(JSON.stringify(zoneCfg.data))
  zpage.setData = function (patch) { Object.assign(zpage.data, patch) }
  zpage.getTabBar = () => null
  zpage.onShow()
  await new Promise((r) => setTimeout(r, 200))
  check('功能区页构建 7 个入口', zpage.data.entries.length === 7, zpage.data.entries.length)
  check('功能区页 loading 归零', zpage.data.loading === false)
  check('功能区页没有 partial 警告', zpage.data.partial === false)
  const zj = []
  mp.navigateTo = (o) => zj.push(o.url)
  zpage.goEntry({ currentTarget: { dataset: { url: '/pages/goods/list' } } })
  zpage.goEntry({ currentTarget: { dataset: { url: '/pages/wall/index' } } })
  check('功能区入口用 navigateTo（非 tab 页）', zj[0] === '/pages/goods/list', zj[0])
  check('功能区入口带正确落点', zj[1] === '/pages/wall/index', zj[1])

  console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
  process.exit(fail ? 1 : 0)
}

main().catch((e) => {
  console.error('测试执行异常：', e)
  process.exit(1)
})
