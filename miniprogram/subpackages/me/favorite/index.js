/**
 * 我的 · 赞与收藏
 * 数据：GET /goods/favorites
 *   后端实现是「我点过赞的闲置」= t_like(target_type=goods)，所以数量与
 *   「我的」页头部 favoriteCount 同源，取消收藏即取消点赞（POST /goods/{id}/like 为切换语义）。
 */
const http = require('../../../utils/request')
const auth = require('../../../utils/auth')
const { mp, timeAgo, priceText, toast } = require('../../../utils/util')
const { resolveList } = require('../../../utils/img')

Page({
  data: {
    list: [],
    shown: [],
    loading: true,
    loadError: false,
    needLogin: false,
    /** 正在取消收藏的 id，防连点造成重复请求 */
    removing: ''
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
      return http.get('/goods/favorites', null, { silent: true })
    }).then(function (list) {
      const items = (list || []).map(decorate)
      self.setData({
        list: items,
        shown: items,
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

  /** 取消收藏：二次确认 → 调切换接口 → 本地移除（不整页重拉，省一次请求） */
  unfavorite: function (e) {
    const self = this
    const id = e.currentTarget.dataset.id
    if (!id || this.data.removing) return
    mp.showModal({
      title: '取消收藏',
      content: '取消后这件闲置会从收藏里移除，可以随时在详情页重新收藏。',
      confirmText: '取消收藏',
      confirmColor: '#DC2626',
      cancelText: '再想想',
      success: function (res) {
        if (!res.confirm) return
        self.setData({ removing: id })
        http.post('/goods/' + id + '/like', {}, { silent: true }).then(function () {
          const list = self.data.list.filter(function (g) { return g.id !== id })
          self.setData({ list: list, shown: list, removing: '' })
          toast('已取消收藏')
        }).catch(function () {
          self.setData({ removing: '' })
          toast('操作失败，请稍后重试')
        })
      }
    })
  },

  goGoods: function () {
    mp.navigateTo({ url: '/pages/goods/list' })
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

function decorate(g) {
  const imgs = resolveList(g.imageList || [])
  return Object.assign({}, g, {
    id: String(g.id),
    cover: imgs[0] || '',
    timeText: timeAgo(g.createdAt),
    priceText: priceText(g.price)
  })
}
