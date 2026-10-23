const http = require('../../utils/request')
const { timeAgo, priceText, mp, categoryEmoji, conditionText, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')
const { resolveList } = require('../../utils/img')

const EMOJIS = [
  '😀', '😃', '😄', '😁', '😊', '🥰', '😍', '😘',
  '😜', '🤪', '😳', '🥺', '😭', '😅', '😴', '🤔',
  '🙃', '😇', '🤗', '😎', '🥳', '😤', '😱', '🤯',
  '👍', '👏', '🙏', '💪', '🤝', '✌️', '👀', '🎉',
  '❤️', '💔', '💌', '🌙', '⭐', '🌸', '🍀', '🌈'
]

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
    submitting: false,
    comments: [],
    commentInput: '',
    commentImages: [],
    commentFocus: false,
    showEmoji: false,
    emojiList: EMOJIS,
    sending: false,
    replyTo: null
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
      g.imgFailed = []
      const me = getApp().globalData.user
      const isOwner = !!(me && g.userId && String(me.id) === String(g.userId))
      // 头像快照可能落后于最新资料，自己的商品用本地头像兜底
      if (isOwner && !g.sellerAvatar && me.avatar) {
        g.sellerAvatar = me.avatar
      }

      // ---- 以下展示数据全部来自发布时填写的真实字段，无值则自动隐藏或兜底 ----
      g.saveText = g.originalPrice && g.originalPrice > g.price
        ? '省 ¥' + priceText(g.originalPrice - g.price)
        : ''
      g.sellerMeta = [
        { top: '在售 ' + (g.sellerSaleCount || 0) + ' 件', sub: '实时在售', icon: '🏅', brand: true },
        { top: '累计发布 ' + (g.sellerTotalCount || 0) + ' 件', sub: '校园认证卖家', icon: '' }
      ]
      const specs = []
      const conditionLabel = conditionText(g.conditionLevel)
      if (conditionLabel) {
        specs.push({ icon: '✨', label: '成色', value: conditionLabel, warn: false })
      }
      if (g.purchaseTime) {
        specs.push({ icon: '🗓️', label: '入手时间', value: g.purchaseTime, warn: false })
      }
      const usageText = [g.usageFrequency, g.source].filter(Boolean).join(' · ')
      if (usageText) {
        specs.push({ icon: '💻', label: '使用频率与来源', value: usageText, warn: false })
      }
      if (g.warranty) {
        specs.push({
          icon: '🛡️',
          label: '保修状态',
          value: g.warranty + (g.supportTrial === 1 ? ' · 支持面交试机' : ''),
          warn: g.warranty !== '官方保修中'
        })
      }
      specs.push({
        icon: '🚚',
        label: '交易方式',
        value: (g.tradeType === 1 ? '限校内自提' : '支持邮寄') + (g.location ? '（' + g.location + '）' : ''),
        warn: false,
        brand: true
      })
      g.specs = specs
      // 校友问答需登录后实时拉取，当前版本不展示演示数据
      g.qaList = []

      self.setData({
        goods: g,
        loading: false,
        loadError: false,
        liked: !!g.liked,
        isOwner: isOwner
      })
      mp.setNavigationBarTitle({ title: g.title })
      self.loadComments()
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

  /** 轮播图加载失败：该张降级为分类图标，避免白块 */
  onImgError: function (e) {
    const index = e.currentTarget.dataset.index
    if (index === undefined || index === null) return
    const patch = {}
    patch['goods.imgFailed[' + index + ']'] = true
    this.setData(patch)
  },

  /** 卖家头像加载失败：回退默认头像 */
  onAvatarError: function () {
    this.setData({ 'goods.sellerAvatar': '' })
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

  /* ---------- 评论（含楼中楼回复） ---------- */
  loadComments: function () {
    const self = this
    http.get('/goods/' + this.goodsId + '/comments', null, { silent: true }).then(function (list) {
      const arr = (list || []).map(function (c) {
        c.timeText = timeAgo(c.createdAt)
        c.imageList = resolveList(c.imageList)
        c.expanded = false
        c.replyCount = c.replyCount || (c.replies || []).length
        c.replies = (c.replies || []).map(function (r) {
          r.timeText = timeAgo(r.createdAt)
          r.imageList = resolveList(r.imageList)
          return r
        })
        // WXML 不支持数组方法，默认展示的回复列表在 JS 里预计算
        c.repliesShown = c.replies.slice(0, 5)
        return c
      })
      self.setData({ comments: arr })
    }).catch(function () {})
  },

  focusInput: function () {
    this.setData({ commentFocus: true, showEmoji: false })
  },

  onCommentInput: function (e) {
    this.setData({ commentInput: e.detail.value })
  },

  toggleEmoji: function () {
    this.setData({ showEmoji: !this.data.showEmoji, commentFocus: false })
  },

  addEmoji: function (e) {
    const next = this.data.commentInput + e.currentTarget.dataset.emoji
    if (next.length > 500) {
      toast('评论最多 500 字')
      return
    }
    this.setData({ commentInput: next })
  },

  chooseCommentImage: function () {
    const self = this
    const rest = 3 - this.data.commentImages.length
    if (rest <= 0) {
      toast('最多 3 张图片')
      return
    }
    mp.chooseImage({
      count: rest,
      sizeType: ['compressed'],
      success: function (res) {
        self.setData({
          commentImages: self.data.commentImages.concat(res.tempFilePaths),
          showEmoji: false
        })
      }
    })
  },

  removeCommentImage: function (e) {
    const list = this.data.commentImages.slice()
    list.splice(e.currentTarget.dataset.index, 1)
    this.setData({ commentImages: list })
  },

  replyComment: function (e) {
    const ds = e.currentTarget.dataset
    // 先置 false 再置 true：刚发过评论时输入框往往仍处于聚焦态，
    // 直接设 true 不会产生 false→true 跳变，WeChat 不会重新弹键盘。
    // 延迟一帧再聚焦，避免「点了回复却输入不了」。
    this.setData({
      replyTo: { id: ds.id, name: ds.name },
      commentFocus: false,
      showEmoji: false
    })
    const self = this
    setTimeout(function () {
      self.setData({ commentFocus: true })
    }, 150)
  },

  cancelReply: function () {
    this.setData({ replyTo: null })
  },

  toggleReplies: function (e) {
    const index = e.currentTarget.dataset.index
    const comment = this.data.comments[index]
    const expanded = !comment.expanded
    const patch = {}
    patch['comments[' + index + '].expanded'] = expanded
    patch['comments[' + index + '].repliesShown'] = expanded ? comment.replies : comment.replies.slice(0, 5)
    this.setData(patch)
  },

  sendComment: function () {
    const self = this
    const content = this.data.commentInput.trim()
    const images = this.data.commentImages
    if (!content && !images.length) {
      toast('说点什么，或者配张图吧')
      return
    }
    if (this.data.sending) {
      toast('正在发送，请稍候')
      return
    }
    this.setData({ sending: true })
    if (images.length) mp.showLoading({ title: '上传图片中', mask: true })
    auth.ensureVerified().then(function () {
      return upload.uploadImages(images)
    }).then(function (urls) {
      if (images.length) mp.hideLoading()
      const payload = { content: content, images: urls }
      if (self.data.replyTo) payload.parentId = self.data.replyTo.id
      return http.post('/goods/' + self.goodsId + '/comments', payload)
    }).then(function () {
      self.setData({
        commentInput: '',
        commentImages: [],
        showEmoji: false,
        commentFocus: false,
        sending: false,
        replyTo: null
      })
      self.loadComments()
      self.load(true)
      mp.setStorageSync('needRefreshGoods', 1)
    }).catch(function () {
      self.setData({ sending: false })
      mp.hideLoading()
    })
  },

  deleteComment: function (e) {
    const self = this
    const id = e.currentTarget.dataset.id
    mp.showModal({
      title: '删除这条评论？',
      content: '删除后不可恢复',
      confirmColor: '#ff4d4f',
      success: function (res) {
        if (!res.confirm) return
        http.del('/goods/' + self.goodsId + '/comments/' + id).then(function () {
          toast('已删除')
          self.loadComments()
          self.load(true)
          mp.setStorageSync('needRefreshGoods', 1)
        }).catch(function () {})
      }
    })
  },

  previewCommentImage: function (e) {
    const url = e.currentTarget.dataset.url
    mp.previewImage({ current: url, urls: [url] })
  },

  /** 关注卖家：占位交互 */
  onFollow: function () {
    toast('关注功能即将上线，先「聊一聊」吧')
  }
})
