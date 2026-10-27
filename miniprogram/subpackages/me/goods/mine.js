/**
 * 我的 · 我发布的（闲置）
 * 分包：subpackages/me —— 主包只放首页 / 功能区 / 消息 / 我的 四个 tab 页，
 * 这类「点进去才用」的二级页统一放分包，配 app.json 的 preloadRule 做预下载。
 *
 * 数据：GET /goods/mine 一次拿全量（本人商品量小，服务端无分页参数），
 * 分段 tab 走本地过滤，切 tab 不发请求 → 秒切。
 */
const http = require('../../../utils/request')
const auth = require('../../../utils/auth')
const { mp, timeAgo, priceText } = require('../../../utils/util')
const { resolveList } = require('../../../utils/img')

/** 与后端 t_goods.status 对齐：0 已下架 / 1 在售 / 2 已售出 */
const TABS = [
  { key: 'all', label: '全部' },
  { key: 'on', label: '在售' },
  { key: 'sold', label: '已售出' },
  { key: 'off', label: '已下架' }
]

Page({
  data: {
    tabs: [],
    tab: 'all',
    list: [],      // 全量（所有状态）
    shown: [],     // 当前 tab 过滤结果
    loading: true,
    loadError: false,
    needLogin: false
  },

  /**
   * 只在 onShow 里拉数据：
   * 从「商品详情」下架 / 改价 / 删除后返回，必须看到最新状态，所以每次进页面都刷一次。
   * 首次进入 onShow 一定在 onLoad 之后触发，不需要再在 onLoad 里拉一遍（避免双请求）。
   */
  onShow: function () {
    this.load()
  },

  onPullDownRefresh: function () {
    this.load(true)
  },

  load: function (refreshing) {
    const self = this
    if (!refreshing && !this.data.list.length) this.setData({ loading: true })
    this.setData({ loadError: false })

    auth.ensureLogin().then(function () {
      return http.get('/goods/mine', null, { silent: true })
    }).then(function (list) {
      self.setData({
        list: (list || []).map(decorate),
        loading: false,
        loadError: false,
        needLogin: false
      })
      self.applyTab(self.data.tab)
      mp.stopPullDownRefresh()
    }).catch(function (err) {
      // 令牌失效（401）/ 没登录：给出登录入口；其余按网络错误处理
      const noAuth = !!(err && (err.code === 401 || err.code === 403)) || !http.getToken()
      self.setData({ loading: false, loadError: !noAuth, needLogin: noAuth })
      mp.stopPullDownRefresh()
    })
  },

  /** 切 tab：只改 shown，不重新请求 */
  switchTab: function (e) {
    const key = e.currentTarget.dataset.key
    if (!key || key === this.data.tab) return
    this.applyTab(key)
  },

  applyTab: function (key) {
    const list = this.data.list
    const countOf = function (k) {
      if (k === 'all') return list.length
      return list.filter(function (g) { return g.statusKey === k }).length
    }
    this.setData({
      tab: key,
      tabs: TABS.map(function (t) {
        return { key: t.key, label: t.label, count: countOf(t.key) }
      }),
      shown: key === 'all' ? list : list.filter(function (g) { return g.statusKey === key })
    })
  },

  goDetail: function (e) {
    const id = e.currentTarget.dataset.id
    // id 全程字符串（雪花 19 位，Number() 会丢精度），这里不转数字
    mp.navigateTo({ url: '/pages/goods/detail?id=' + id })
  },

  goPublish: function () {
    auth.ensureVerified().then(function () {
      mp.navigateTo({ url: '/pages/goods/publish' })
    }).catch(function () {})
  },

  retry: function () {
    this.load()
  },

  login: function () {
    const self = this
    auth.ensureLogin(true).then(function () {
      self.load()
    }).catch(function () {})
  },

  /** 图片加载失败：标记该条走占位图，避免灰块 */
  onImgError: function (e) {
    const index = e.currentTarget.dataset.index
    if (index === undefined || index === null) return
    const patch = {}
    patch['shown[' + index + '].imgFailed'] = true
    this.setData(patch)
  }
})

const STATUS_TEXT = { 1: '在售', 2: '已售出', 0: '已下架' }
const STATUS_KEY = { 1: 'on', 2: 'sold', 0: 'off' }

/** 统一整理成视图模型（时间、价格、状态、封面图都在这里算好，wxml 不做判断） */
function decorate(g) {
  const imgs = resolveList(g.imageList || [])
  const status = g.status === 2 ? 2 : (g.status === 0 ? 0 : 1)
  return Object.assign({}, g, {
    id: String(g.id),
    cover: imgs[0] || '',
    timeText: timeAgo(g.createdAt),
    priceText: priceText(g.price),
    statusKey: STATUS_KEY[status],
    statusText: STATUS_TEXT[status]
  })
}
