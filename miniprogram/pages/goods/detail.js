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
    galleryIndex: 1,
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
      const isOwner = !!(me && g.userId && String(me.id) === String(g.userId))
      // 头像快照可能落后于最新资料，自己的商品用本地头像兜底
      if (isOwner && !g.sellerAvatar && me.avatar) {
        g.sellerAvatar = me.avatar
      }

      // ---- 演示数据（先保证页面展示，后续接后端时替换为真实接口） ----
      g.saveText = g.originalPrice && g.originalPrice > g.price
        ? '省 ¥' + priceText(g.originalPrice - g.price)
        : ''
      g.badges = [{ text: (g.categoryName || '闲置') + ' · 成色不错', mint: true }, { text: '支持自提验机' }]
      g.sellerMeta = [
        { top: '信用极好 780', sub: '同校互信', icon: '🏅' },
        { top: '<5分钟', sub: '平均即刻回复', icon: '' },
        { top: '在售 3 · 评价 28', sub: '100% 好评度', icon: '' }
      ]
      g.specs = [
        { icon: '🗓️', label: '入手时间', value: '2023年9月（开学购入）', warn: false },
        { icon: '💻', label: '使用频率与来源', value: '实验室备用机自用 · 无拆修', warn: false },
        { icon: '🛡️', label: '保修状态', value: '官方保修已过 · 支持面交试机', warn: true },
        { icon: '🚚', label: '交易方式', value: (g.tradeType === 1 ? '限校内自提' : '支持邮寄') + (g.location ? '（' + g.location + '）' : ''), warn: false, brand: true }
      ]
      g.qaList = [
        {
          q: '同学还在吗？支持今天傍晚去现场看机通电测试吗？',
          a: '在的呀！今晚 7 点后我都在寝室，可以当场测试～',
          time: '15分钟前'
        },
        {
          q: '跑 Python 日常数据分析会卡顿吗？配件单出吗？',
          a: '跑常规 Notebook 很流畅！配件是随主机打包送的，不单出哈。',
          time: '2小时前'
        },
        {
          q: '新生爽快自提，可以小刀 50 块当路费嘛？^_^',
          a: '好嘞！点下方「聊一聊」，面交时给你减 50 带走～',
          time: '昨天'
        }
      ]

      self.setData({
        goods: g,
        loading: false,
        loadError: false,
        liked: !!g.liked,
        isOwner: isOwner
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

  /** 轮播计数胶囊 1/N */
  onGalleryChange: function (e) {
    this.setData({ galleryIndex: (e.detail.current || 0) + 1 })
  },

  /**
   * 聊一聊：先向后端发起（或复用）会话，拿到会话 id 再进聊天页。
   * 没有卖家 id 时（如匿名/异常数据）退回到纯展示会话。
   */
  goChat: function () {
    const self = this
    const g = this.data.goods
    if (!g) return
    const fallbackName = g.sellerName || '卖家'
    if (!g.userId) {
      mp.navigateTo({
        url: '/pages/chat/index?name=' + encodeURIComponent(fallbackName) + '&goods=' + encodeURIComponent(g.title || '')
      })
      return
    }
    auth.ensureLogin().then(function () {
      return http.post('/messages/start', {
        peerId: g.userId,
        goodsId: g.id,
        goodsTitle: g.title || '',
        goodsPrice: String(g.price == null ? '' : g.price),
        goodsEmoji: g.emoji || '',
        goodsStatus: g.status == null ? 1 : g.status
      }, { loading: '连接中' })
    }).then(function (convo) {
      self.openChat(convo.id)
    }).catch(function () {
      // 建会话失败时把卖家信息带走，由聊天页兜底重试
      self.openChat('')
    })
  },

  /** 跳转聊天页：统一带上会话 id 与商品快照参数 */
  openChat: function (convoId) {
    const g = this.data.goods
    mp.navigateTo({
      url: '/pages/chat/index?id=' + (convoId || '') +
        '&peer=' + (g.userId || '') +
        '&gid=' + (g.id || '') +
        '&name=' + encodeURIComponent(g.sellerName || '卖家') +
        '&goods=' + encodeURIComponent(g.title || '') +
        '&gp=' + encodeURIComponent(String(g.price == null ? '' : g.price)) +
        '&ge=' + encodeURIComponent(g.emoji || '') +
        '&gs=' + encodeURIComponent(String(g.status == null ? 1 : g.status))
    })
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

  stop: function () {},

  /** 关注卖家：占位交互 */
  onFollow: function () {
    toast('关注功能即将上线，先「聊一聊」吧')
  }
})
