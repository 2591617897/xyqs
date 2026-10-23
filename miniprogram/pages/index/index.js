const http = require('../../utils/request')
const { timeAgo, priceText, mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const { resolveList } = require('../../utils/img')

Page({
  data: {
    user: null,
    schoolName: '',
    channels: [],
    channelId: '',
    categories: [],
    feed: [],
    loading: true,
    loadError: false,
    errorMsg: '',
    unread: 0
  },

  onShow: function () {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 })
    }
    const user = getApp().globalData.user
    this.setData({
      user: user,
      schoolName: (user && user.schoolName) || '选择学校'
    })
    this.syncMe()
    this.loadData()
    this.loadUnread()
  },

  /**
   * 静默对齐首页学校名与认证学籍信息。
   *
   * 学校名只有在完成校园认证后才产生，登录接口未必带回，且首页本身感知不到认证状态变化，
   * 所以每次进首页都静默拉一次 /user/me 校正胶囊文案；失败完全静默，不影响页面其它内容。
   */
  syncMe: function () {
    const self = this
    if (!http.getToken()) {
      // 未登录：保持「选择学校」，不做任何提示
      this.setData({ schoolName: '选择学校' })
      return
    }
    http.get('/user/me', null, { silent: true }).then(function (me) {
      if (!me) return
      const merged = auth.updateUser({
        id: me.id,
        nickname: me.nickname,
        avatar: me.avatar,
        schoolId: me.schoolId,
        schoolName: me.schoolName || '',
        verifyStatus: me.verifyStatus
      })
      self.setData({
        user: merged,
        schoolName: (merged && merged.schoolName) || '选择学校'
      })
    }).catch(function () {
      // 静默失败：保留缓存里的学校名，不打扰首页
    })
  },

  /** 铃铛角标：未读私信数，失败静默（未登录/离线时不打搅首页） */
  loadUnread: function () {
    const self = this
    if (!getApp().globalData.user) {
      this.setData({ unread: 0 })
      return
    }
    http.get('/messages/unread', null, { silent: true }).then(function (res) {
      self.setData({ unread: (res && res.unread) || 0 })
    }).catch(function () {
      self.setData({ unread: 0 })
    })
  },

  onPullDownRefresh: function () {
    this.loadData(true)
  },

  retry: function () {
    this.loadData(true)
  },

  loadData: function (refresh) {
    const self = this
    if (refresh) this.setData({ loading: true })

    let firstError = ''
    const fail = function (name) {
      return function (err) {
        if (!firstError && err && err.msg) firstError = err.msg
        console.error('[index] ' + name + ' 加载失败：', err)
        return null
      }
    }

    const postParams = { page: 1, size: 10 }
    if (this.data.channelId) postParams.channelId = this.data.channelId

    // 频道是内容分区，商品（闲置）不属于任何频道。
    // 只有在「全部」这种聚合入口才把闲置混排进来；
    // 否则切了频道看到的还是同一批闲置，会让人以为筛选没生效。
    const goodsTask = this.data.channelId
      ? Promise.resolve(null)
      : http.get('/goods', { page: 1, size: 4, sort: 'latest' }, { silent: true }).catch(fail('goods'))

    Promise.all([
      http.get('/channels', null, { silent: true }).catch(fail('channels')),
      http.get('/posts', postParams, { silent: true }).catch(fail('posts')),
      goodsTask,
      http.get('/goods/categories', null, { silent: true }).catch(fail('categories'))
    ]).then(function (res) {
      const channels = res[0] || []
      const postsRes = res[1] || { records: [] }
      const goodsRes = res[2] || { records: [] }
      const categories = res[3] || []
      const okCount = res.filter(function (x) {
        return !!x
      }).length

      const posts = (postsRes.records || []).map(function (p) {
        return {
          type: 'post',
          key: 'post-' + p.id,
          id: p.id,
          authorName: p.authorName || '校园用户',
          avatar: p.authorAvatar || '',
          avatarText: (p.authorName || '校').substring(0, 1),
          timeText: timeAgo(p.createdAt),
          createdAt: p.createdAt || '',
          badge: p.channelName || '校园频道',
          topic: p.channelName || '校园',
          title: p.title || '',
          excerpt: (p.content || '').replace(/\s+/g, ' '),
          imageList: resolveList(p.imageList),
          attachment: p.attachment || null,
          viewCount: p.viewCount || 0,
          liked: !!p.liked,
          likeCount: p.likeCount || 0,
          commentCount: p.commentCount || 0
        }
      })

      const goods = (goodsRes.records || []).map(function (g) {
        return {
          type: 'goods',
          key: 'goods-' + g.id,
          id: g.id,
          authorName: g.sellerName || '校园用户',
          avatar: g.sellerAvatar || '',
          avatarText: (g.sellerName || '校').substring(0, 1),
          timeText: timeAgo(g.createdAt),
          createdAt: g.createdAt || '',
          badge: g.tradeType === 1 ? '校内自提' : '支持邮寄',
          topic: g.categoryName || '闲置',
          title: g.title || '',
          excerpt: (g.description || '').replace(/\s+/g, ' '),
          priceText: priceText(g.price),
          originalPriceText: g.originalPrice ? priceText(g.originalPrice) : '',
          location: g.location || '',
          viewCount: g.viewCount || 0,
          imageList: resolveList(g.imageList),
          categoryName: g.categoryName || '',
          liked: !!g.liked,
          likeCount: g.likeCount || 0,
          commentCount: 0
        }
      })

      // 帖子与闲置按发布时间混排，形成信息流
      const feed = posts.concat(goods).sort(function (a, b) {
        return (b.createdAt || '').localeCompare(a.createdAt || '')
      })

      self.setData({
        channels: channels,
        categories: categories,
        feed: feed,
        loading: false,
        loadError: okCount === 0,
        errorMsg: okCount === 0 ? (firstError || '加载失败，请检查后端服务') : ''
      })
    }).then(function () {
      mp.stopPullDownRefresh()
    })
  },

  /* ---------- 顶部导航 ---------- */
  switchChannel: function (e) {
    const id = e.currentTarget.dataset.id || ''
    if (String(id) === String(this.data.channelId)) return
    this.setData({ channelId: id, feed: [], loading: true })
    this.loadData()
  },

  openGoodsTab: function (mode, categoryId) {
    const app = getApp()
    if (app.globalData) app.globalData.goodsMode = mode || ''
    if (categoryId) mp.setStorageSync('pendingCategory', categoryId)
    mp.switchTab({ url: '/pages/goods/list' })
  },

  goSearch: function () {
    this.openGoodsTab('')
  },

  goCategory: function (e) {
    this.openGoodsTab('', e.currentTarget.dataset.id)
  },

  /** 公告位：跳蚤集市 → 闲置广场 */
  onAnnounce: function () {
    this.openGoodsTab('')
  },

  goMessages: function () {
    mp.navigateTo({ url: '/pages/messages/index' })
  },

  goStatus: function () {
    const self = this
    const user = getApp().globalData.user
    if (user && user.verifyStatus === 2) {
      mp.switchTab({ url: '/pages/user/index' })
      return
    }
    if (!user) {
      auth.ensureLogin().then(function (u) {
        self.setData({ user: u })
        if (u.verifyStatus === 2) mp.switchTab({ url: '/pages/user/index' })
        else mp.navigateTo({ url: '/pages/user/verify' })
      }).catch(function () {})
      return
    }
    mp.navigateTo({ url: '/pages/user/verify' })
  },

  /* ---------- 信息流交互 ---------- */
  goDetail: function (e) {
    const type = e.currentTarget.dataset.type
    const id = e.currentTarget.dataset.id
    const url = type === 'goods'
      ? '/pages/goods/detail?id=' + id
      : '/pages/post/detail?id=' + id
    mp.navigateTo({ url: url })
  },

  previewFeed: function (e) {
    const fi = e.currentTarget.dataset.fi
    const item = this.data.feed[fi]
    if (!item || !item.imageList.length) return
    mp.previewImage({
      current: e.currentTarget.dataset.url,
      urls: item.imageList
    })
  },

  toggleLike: function (e) {
    const self = this
    const index = e.currentTarget.dataset.index
    const item = this.data.feed[index]
    if (!item) return
    auth.ensureLogin().then(function () {
      const url = item.type === 'goods'
        ? '/goods/' + item.id + '/like'
        : '/posts/' + item.id + '/like'
      return http.post(url)
    }).then(function (liked) {
      const patch = {}
      patch['feed[' + index + '].liked'] = liked
      patch['feed[' + index + '].likeCount'] = Math.max(0, (item.likeCount || 0) + (liked ? 1 : -1))
      self.setData(patch)
    }).catch(function () {})
  },

  goComments: function (e) {
    const type = e.currentTarget.dataset.type
    const id = e.currentTarget.dataset.id
    if (type === 'goods') {
      mp.navigateTo({ url: '/pages/goods/detail?id=' + id })
      return
    }
    mp.navigateTo({ url: '/pages/post/detail?id=' + id })
  },

  /** 关注按钮：占位交互 */
  onFollow: function () {
    toast('关注功能即将上线，先去评论区互动吧')
  },

  /** 附件卡片：占位交互 */
  onAttachment: function () {
    toast('附件下载即将开放')
  },

  /** 阻止分享按钮的 tap 冒泡到卡片详情跳转 */
  noop: function () {},

  onShareAppMessage: function () {
    return {
      title: '校园趣事 · 校园闲置与圈子社区',
      path: '/pages/index/index'
    }
  }
})
