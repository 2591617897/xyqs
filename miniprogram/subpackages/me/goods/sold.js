/**
 * 我的 · 已卖出（成交记录）
 * 数据：GET /goods/mine?status=2 —— 与「我的」页头部 soldCount 同一个口径
 * （UserService 里 soldCount = goodsCount(userId, 2)），所以两边数字一定对得上。
 */
const http = require('../../../utils/request')
const auth = require('../../../utils/auth')
const { mp, timeAgo, priceText } = require('../../../utils/util')
const { resolveList } = require('../../../utils/img')

Page({
  data: {
    list: [],
    shown: [],
    totalAmount: '0',
    loading: true,
    loadError: false,
    needLogin: false
  },

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
      // status=2 = 已售出（0 已下架 / 1 在售 / 2 已售出）
      return http.get('/goods/mine', { status: 2 }, { silent: true })
    }).then(function (list) {
      const items = (list || []).map(decorate)
      let sum = 0
      items.forEach(function (g) { sum += Number(g.price) || 0 })
      self.setData({
        list: items,
        shown: items,
        totalAmount: money(sum),
        loading: false,
        loadError: false,
        needLogin: false
      })
      mp.stopPullDownRefresh()
    }).catch(function (err) {
      const noAuth = !!(err && (err.code === 401 || err.code === 403)) || !http.getToken()
      self.setData({ loading: false, loadError: !noAuth, needLogin: noAuth })
      mp.stopPullDownRefresh()
    })
  },

  goDetail: function (e) {
    mp.navigateTo({ url: '/pages/goods/detail?id=' + e.currentTarget.dataset.id })
  },

  /** 「我发布的」里可以改状态，卖掉了就切过去，这里给条明路 */
  goMine: function () {
    mp.navigateTo({ url: '/subpackages/me/goods/mine' })
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

  onImgError: function (e) {
    const index = e.currentTarget.dataset.index
    if (index === undefined || index === null) return
    const patch = {}
    patch['shown[' + index + '].imgFailed'] = true
    this.setData(patch)
  }
})

/** 金额展示：整数不带小数，有角分保留两位（和 utils/util.js#priceText 同一套规则） */
function money(n) {
  const v = Number(n) || 0
  return v % 1 === 0 ? String(v) : v.toFixed(2)
}

function decorate(g) {
  const imgs = resolveList(g.imageList || [])
  return Object.assign({}, g, {
    id: String(g.id),
    cover: imgs[0] || '',
    timeText: timeAgo(g.createdAt),
    priceText: priceText(g.price)
  })
}
