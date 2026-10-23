const http = require('../../utils/request')
const { timeAgo, priceText, mp, categoryEmoji, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const { resolveList } = require('../../utils/img')

Page({
  data: {
    goods: null,
    loading: true,
    loadError: false,
    isOwner: false,
    liked: false,
    showOrder: false,
    message: '',
    contact: '',
    submitting: false
  },

  onLoad: function (options) {
    this.goodsId = options.id
    this.load()
  },

  onShow: function () {
    if (this.goodsId) this.load(true)
  },

  load: function (silent) {
    const self = this
    if (!silent) this.setData({ loading: true })
    http.get('/goods/' + this.goodsId, null, { silent: true }).then(function (g) {
      g.timeText = timeAgo(g.createdAt)
      g.priceText = priceText(g.price)
      g.emoji = categoryEmoji(g.categoryId)
      g.imageList = resolveList(g.imageList)
      const me = getApp().globalData.user
      self.setData({
        goods: g,
        loading: false,
        loadError: false,
        liked: !!g.liked,
        isOwner: !!(me && g.userId && String(me.id) === String(g.userId))
      })
      mp.setNavigationBarTitle({ title: g.title })
    }).catch(function (err) {
      const notFound = err && err.code === 404
      self.setData({ loading: false, loadError: !notFound })
      if (!notFound) toast('加载失败，请稍后重试')
    })
  },

  retry: function () {
    this.load()
  },

  /* ---------- 自己发布的管理操作 ---------- */
  goEdit: function () {
    mp.navigateTo({ url: '/pages/goods/publish?id=' + this.goodsId })
  },

  toggleStatus: function () {
    const self = this
    const goods = this.data.goods
    const next = goods.status === 1 ? 0 : 1
    http.post('/goods/' + this.goodsId + '/status?status=' + next, null, { loading: true })
      .then(function () {
        toast(next === 1 ? '已重新上架' : '已下架')
        mp.setStorageSync('needRefreshGoods', 1)
        self.load(true)
      }).catch(function () {})
  },

  removeGoods: function () {
    const self = this
    mp.showModal({
      title: '删除这件闲置？',
      content: '删除后不可恢复',
      confirmColor: '#ff4d4f',
      success: function (res) {
        if (!res.confirm) return
        http.del('/goods/' + self.goodsId, null, { loading: '删除中' }).then(function () {
          toast('已删除')
          mp.setStorageSync('needRefreshGoods', 1)
          setTimeout(function () {
            mp.navigateBack()
          }, 600)
        }).catch(function () {})
      }
    })
  },

  preview: function (e) {
    const urls = this.data.goods.imageList || []
    if (!urls.length) return
    mp.previewImage({ current: e.currentTarget.dataset.url, urls: urls })
  },

  toggleLike: function () {
    const self = this
    auth.ensureLogin().then(function () {
      return http.post('/goods/' + self.goodsId + '/like')
    }).then(function (liked) {
      self.setData({ liked: liked })
      const g = self.data.goods
      g.likeCount = Math.max(0, (g.likeCount || 0) + (liked ? 1 : -1))
      self.setData({ goods: g })
      toast(liked ? '已收藏' : '已取消收藏')
    }).catch(function () {})
  },

  openOrder: function () {
    const self = this
    const g = this.data.goods
    if (!g) return
    if (g.status !== 1) {
      toast('该商品已售出或已下架')
      return
    }
    auth.ensureVerified().then(function () {
      self.setData({ showOrder: true })
    }).catch(function () {})
  },

  closeOrder: function () {
    this.setData({ showOrder: false })
  },

  onMessage: function (e) {
    this.setData({ message: e.detail.value })
  },

  onContact: function (e) {
    this.setData({ contact: e.detail.value })
  },

  submitOrder: function () {
    const self = this
    if (!this.data.contact.trim()) {
      toast('请留下联系方式')
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })
    http.post('/goods/' + this.goodsId + '/order', {
      message: this.data.message,
      contact: this.data.contact
    }).then(function () {
      self.setData({ showOrder: false, submitting: false, message: '', contact: '' })
      mp.showModal({
        title: '已通知卖家',
        content: '卖家会在二手页的「收到的意向」里看到你的联系方式，也可以主动私聊卖家哦。',
        showCancel: false,
        confirmText: '知道了'
      })
    }).catch(function () {
      self.setData({ submitting: false })
    })
  },

  stop: function () {}
})
