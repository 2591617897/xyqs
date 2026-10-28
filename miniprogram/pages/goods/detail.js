const http = require('../../utils/request')
const { timeAgo, priceText, mp, categoryEmoji, conditionText, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')
const followMod = require('../../utils/follow')
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
    following: false,
    followLoading: false,
    galleryIndex: 1,
    showOrder: false,
    message: '',
    contact: '',
    submitting: false,
    comments: [],
    commentInput: '',
    commentImages: [],
    showCommentSheet: false,
    commentSheetFocus: false,
    /** 键盘高度：底部弹层是 fixed 定位，不手动上移会被键盘挡住输入框 */
    keyboardHeight: 0,
    showEmoji: false,
    emojiList: EMOJIS,
    sending: false,
    replyTo: null,
    /** @ 选择器 */
    mentionUserIds: [],
    mentionPickerList: [],
    showMentionPicker: false,
    /** 推广弹层 */
    showPromote: false,
    /** 互通推荐：有人在找这类东西（需求广场） */
    wanted: []
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
        { top: '累计发布 ' + (g.sellerTotalCount || 0) + ' 件', sub: '已完成校园认证', icon: '' }
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
        value: (g.tradeType === 1 ? '仅限校内当面自提' : '仅限校内当面自提') + (g.location ? '（' + g.location + '）' : ''),
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
      // 非本人且有卖家 id：静默拉关注状态初始化按钮文案（未登录返回 false，不打扰）
      if (!isOwner && g.userId) {
        http.get('/user/follow/status?userId=' + g.userId, null, { silent: true }).then(function (res) {
          self.setData({ following: !!res })
        }).catch(function () {})
      }
      // 互通推荐：匹配失败不影响主流程（区块不渲染而已），所以单独 catch
      self.loadWanted()
    }).catch(function (err) {
      const notFound = err && err.code === 404
      self.setData({ loading: false, loadError: !notFound })
      if (!notFound) toast('加载失败，请稍后重试')
    })
  },

  /**
   * 「我在卖自行车」→ 推正在找这类东西的需求。
   * 卖家可以主动去回应，不用等对方搜到自己。
   */
  loadWanted: function () {
    const self = this
    http.get('/goods/' + this.goodsId + '/demands', null, { silent: true })
      .then(function (list) {
        self.setData({
          wanted: (list || []).map(function (d) {
            d.typeLabel = d.type === 2 ? '求帮忙' : '求物'
            d.typeIcon = d.type === 2 ? '🙋' : '🎁'
            d.budgetText = d.budget === null || d.budget === undefined ? '面议' : '¥' + d.budget
            return d
          })
        })
      })
      .catch(function () {})
  },

  goDemandDetail: function (e) {
    mp.navigateTo({ url: '/subpackages/demand/detail?id=' + e.currentTarget.dataset.id })
  },

  retry: function () {
    this.load()
  },

  /* ---------- 自己发布的管理操作 ---------- */
  goEdit: function () {
    mp.navigateTo({ url: '/pages/goods/publish?id=' + this.goodsId })
  },

  /* ---------- 推广：看一段激励视频换排序 ---------- */
  openPromote: function () {
    this.setData({ showPromote: true })
  },

  closePromote: function () {
    this.setData({ showPromote: false })
  },

  /** 结算完成：把新分数写回本地卡片，并标记列表需要刷新 */
  onPromoteDone: function (e) {
    const score = e && e.detail ? e.detail.score : 0
    this.setData({ 'goods.promoteScore': score, showPromote: false })
    mp.setStorageSync('needRefreshGoods', 1)
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
  goReport: function () {
    const g = this.data.goods
    if (!g) return
    mp.navigateTo({ url: '/pages/report/report?type=GOODS&id=' + g.id })
  },

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
    this.setData({ showOrder: false, keyboardHeight: 0 })
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
        content: '卖家会在闲置页的「收到的意向」里看到你的联系方式，也可以主动私聊卖家。\n\n提醒：本平台没有支付功能，请务必在校内当面验货、当面结清，不要提前转账。',
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
    this.setData({ commentSheetFocus: true, showEmoji: false })
  },

  openCommentSheet: function () {
    // 先 false 再 true 让 WeChat 触发键盘弹起
    this.setData({ showCommentSheet: true, commentSheetFocus: false, showEmoji: false })
    const self = this
    setTimeout(function () {
      self.setData({ commentSheetFocus: true })
    }, 120)
  },

  closeCommentSheet: function () {
    this.setData({
      showCommentSheet: false,
      commentSheetFocus: false,
      showEmoji: false,
      replyTo: null,
      keyboardHeight: 0
    })
  },

  /**
   * 键盘高度变化 → 弹层整体上移，避免输入框和按钮被键盘挡住。
   * fixed 定位的元素不会被微信的 adjust-position 顶起，所以输入框上关掉它、手动抬。
   */
  onKeyboardHeight: function (e) {
    const h = (e.detail && e.detail.height) || 0
    if (h !== this.data.keyboardHeight) {
      this.setData({ keyboardHeight: h })
    }
  },

  openMentionPicker: function () {
    const self = this
    mp.showLoading({ title: '加载好友中', mask: true })
    http.get('/user/follow/list', { type: 'following' }, { silent: true }).then(function (res) {
      mp.hideLoading()
      // FollowController#list 返回 R<List<FollowUserVO>>，request 解包后是裸数组；
      // 这里同时兼容未来改成 {list:[]} 分页对象的情况
      const list = Array.isArray(res) ? res : ((res && res.list) || [])
      const filtered = list.filter(function (u) { return !self.data.mentionUserIds.some(function (m) { return m === u.id }) })
      if (!filtered.length) {
        mp.showToast({ title: '暂无可@的好友', icon: 'none' })
        return
      }
      self.setData({ mentionPickerList: filtered, showMentionPicker: true, commentSheetFocus: false, showEmoji: false })
    }).catch(function () {
      mp.hideLoading()
      mp.showToast({ title: '加载失败', icon: 'none' })
    })
  },

  closeMentionPicker: function () {
    const self = this
    this.setData({ showMentionPicker: false, mentionPickerList: [] })
    setTimeout(function () { self.setData({ commentSheetFocus: true }) }, 100)
  },

  confirmMention: function (e) {
    const self = this
    const idx = e.currentTarget.dataset.index
    const user = this.data.mentionPickerList[idx]
    if (!user) return
    const newIds = this.data.mentionUserIds.concat([user.id])
    const remaining = this.data.mentionPickerList.filter(function (_, i) { return i !== idx })
    // textarea 插入 @昵称 空格
    const next = this.data.commentInput + '@' + (user.nickname || user.name || '校友') + ' '
    this.setData({
      mentionUserIds: newIds,
      mentionPickerList: remaining,
      commentInput: next,
      showMentionPicker: false
    })
    setTimeout(function () { self.setData({ commentSheetFocus: true }) }, 120)
  },

  onCommentInput: function (e) {
    this.setData({ commentInput: e.detail.value })
  },

  toggleEmoji: function () {
    const willShow = !this.data.showEmoji
    this.setData({
      showEmoji: willShow,
      commentSheetFocus: false
    })
    // 切回输入时再聚焦
    if (!willShow) {
      const self = this
      setTimeout(function () { self.setData({ commentSheetFocus: true }) }, 100)
    }
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
    this.setData({
      replyTo: { id: ds.id, name: ds.name },
      showEmoji: false
    })
    this.openCommentSheet()
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
      const payload = { content: content, images: urls, mentionUserIds: self.data.mentionUserIds }
      if (self.data.replyTo) payload.parentId = self.data.replyTo.id
      return http.post('/goods/' + self.goodsId + '/comments', payload)
    }).then(function () {
      self.setData({
        commentInput: '',
        commentImages: [],
        showEmoji: false,
        sending: false,
        replyTo: null,
        mentionUserIds: [],
        showCommentSheet: false,
        commentSheetFocus: false,
        keyboardHeight: 0
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

  /** 卖家卡跳转：进对方个人主页（catchtap 已阻止冒泡） */
  goUserSpace: function () {
    const g = this.data.goods
    if (!g || !g.userId) return
    mp.navigateTo({ url: '/pages/user/space/space?id=' + g.userId })
  },

  /**
   * 关注 / 取消关注卖家（乐观更新 + followLoading 防重复点击，失败回滚）。
   */
  onFollow: function () {
    if (this.data.followLoading) return
    const self = this
    const g = this.data.goods
    if (!g || !g.userId) return
    const prev = this.data.following
    const next = !prev
    this.setData({ followLoading: true, following: next })
    followMod.toggle(g.userId, prev).then(function (res) {
      self.setData({ followLoading: false, following: !!res.following })
      toast(next ? '已关注' : '已取消关注')
    }).catch(function () {
      self.setData({ followLoading: false, following: prev })
      toast('操作失败，请重试')
    })
  }
})
