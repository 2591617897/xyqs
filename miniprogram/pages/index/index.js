const http = require('../../utils/request')
const { timeAgo, priceText, mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const scopeMod = require('../../utils/scope')
const followMod = require('../../utils/follow')
const { resolveList } = require('../../utils/img')

Page({
  data: {
    user: null,
    // 浏览范围（本校 / 本市）
    scopeLabel: '选择学校',
    scopeMode: 'school',
    scopeCity: '',
    schoolCity: '',
    scopeShow: false,
    geoCity: null,
    /** 新用户选学校引导 */
    onboardShow: false,
    onboardSaving: false,
    channels: [],
    channelId: '',
    categories: [],
    feed: [],
    loading: true,
    loadError: false,
    errorMsg: '',
    unread: 0,
    hotPost: null,
    hotComment: null,
    // 信息流关注态：{ [authorId]: {following, loading} }。
    // 列表没有批量关注状态接口，关注态只在本页内存中维护，刷新后重置（允许不持久化）
    followedAuthors: {}
  },

  onShow: function () {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 })
    }
    this.applyUser(getApp().globalData.user)
    this.syncMe()
    this.loadData()
    this.loadUnread()
    this.checkOnboard()
  },

  /** 用最新用户信息刷新胶囊文案与范围展示 */
  applyUser: function (user) {
    // 没有学校时「本市」拿不到城市，没有意义 → 一律按「选择学校」展示，
    // 避免残留的 feedScope=city 把胶囊显示成「某市 · 全市」
    const s = (user && user.schoolId) ? scopeMod.read() : { scope: 'school', city: '' }
    this.setData({
      user: user,
      schoolCity: (user && user.schoolCity) || '',
      scopeMode: s.scope,
      scopeCity: s.scope === 'city' ? s.city : '',
      scopeLabel: scopeMod.label(user && user.schoolName, user && user.schoolCity)
    })
  },

  /* ---------- 新用户引导：选学校 ---------- */
  /**
   * 首次进入检查：已登录但还没落学校时弹「选择学校」。
   *
   * 为什么必须补这一步：后端本校筛选写成 `.eq(schoolId != null, ...)`，
   * schoolId 为空等于不过滤，新用户看到的是全站内容，「本校」范围形同虚设。
   * 点过「暂不选择」记一个标记，本次不再打扰（清缓存后重置）。
   */
  checkOnboard: function () {
    const self = this
    if (mp.getStorageSync('onboardSkipped')) return
    const cached = getApp().globalData.user
    if (cached && cached.schoolId) return
    // 首页原本不主动登录，这里为判断是否需要引导走一次静默登录（有缓存时零请求）
    auth.ensureLogin().then(function (user) {
      if (!user || user.schoolId) return
      if (mp.getStorageSync('onboardSkipped')) return
      self.setData({ onboardShow: true })
    }).catch(function () {
      // 登录失败不阻塞浏览
    })
  },

  onOnboardSelect: function (e) {
    const self = this
    const school = e.detail && e.detail.school
    if (!school || this.data.onboardSaving) return
    this.setData({ onboardSaving: true })
    // 组件可能在未登录时被打开（点胶囊进来），先确保有 token 再写库
    auth.ensureLogin().then(function () {
      return http.put('/user/me', { schoolId: school.id })
    }).then(function (me) {
      // schoolId 是登录时写进 JWT 的快照，改库后必须重签 token，
      // 否则网关下发的 X-School-Id 还是旧值，本校筛选不会生效。
      return auth.ensureLogin(true).then(function () {
        return me
      })
    }).then(function (me) {
      const merged = auth.updateUser({
        id: me.id,
        schoolId: me.schoolId,
        schoolName: me.schoolName || school.name || '',
        schoolCity: me.schoolCity || school.city || '',
        verifyStatus: me.verifyStatus
      })
      self.setData({ onboardShow: false, onboardSaving: false })
      // 刚绑定学校时把浏览范围拉回「本校」：
      // 之前若停在「本市」模式，胶囊会显示成「某市 · 全市」，看起来像没绑学校
      scopeMod.write({ scope: 'school' })
      self.applyUser(merged)
      self.loadData()
      toast('已切换到 ' + (me.schoolName || school.name || '本校'))
    }).catch(function () {
      self.setData({ onboardSaving: false })
    })
  },

  onOnboardClose: function () {
    mp.setStorageSync('onboardSkipped', 1)
    this.setData({ onboardShow: false })
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
      this.setData({ scopeLabel: '选择学校' })
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
        schoolCity: me.schoolCity || '',
        verifyStatus: me.verifyStatus
      })
      self.applyUser(merged)
    }).catch(function (err) {
      // 令牌指向的用户已不存在（清库 / 换环境）：重建会话后整页重来一次，
      // 否则首页会一直拿上一轮的缓存显示旧的学校和浏览范围
      if (err && err.code === 404 && !self._reloginTried) {
        self._reloginTried = true
        auth.relogin().then(function (u) {
          self.applyUser(u)
          self.syncMe()
          self.loadData()
          self.checkOnboard()
        }).catch(function () {
          self.setData({ user: null, scopeLabel: '选择学校' })
        })
        return
      }
      // 其余静默失败：保留缓存里的学校名，不打扰首页
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

    // 浏览范围：本校（默认）/ 本市（scope=city&city=xxx）
    const postParams = Object.assign({ page: 1, size: 10 }, scopeMod.postParams())
    if (this.data.channelId) postParams.channelId = this.data.channelId

    // 频道是内容分区，商品（闲置）不属于任何频道。
    // 只有在「全部」这种聚合入口才把闲置混排进来；
    // 否则切了频道看到的还是同一批闲置，会让人以为筛选没生效。
    const goodsTask = this.data.channelId
      ? Promise.resolve(null)
      : http.get('/goods', Object.assign({ page: 1, size: 4, sort: 'latest' }, scopeMod.goodsParams()), { silent: true }).catch(fail('goods'))

    Promise.all([
      http.get('/channels', null, { silent: true }).catch(fail('channels')),
      http.get('/posts', postParams, { silent: true }).catch(fail('posts')),
      goodsTask,
      http.get('/goods/categories', null, { silent: true }).catch(fail('categories')),
      // 全站点赞最高的帖子：点赞不超过 10 不值当展示，接口失败静默跳过
      http.get('/posts', { page: 1, size: 1, sort: 'hot' }, { silent: true }).catch(fail('hot'))
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
          authorId: p.userId,
          authorName: p.authorName || '校园用户',
          avatar: p.authorAvatar || '',
          schoolId: p.schoolId || '',
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
          viewText: fmtCount(p.viewCount),
          liked: !!p.liked,
          likeCount: p.likeCount || 0,
          likeText: fmtCount(p.likeCount),
          commentCount: p.commentCount || 0,
          commentText: fmtCount(p.commentCount)
        }
      })

      const goods = (goodsRes.records || []).map(function (g) {
        return {
          type: 'goods',
          key: 'goods-' + g.id,
          id: g.id,
          authorId: g.userId,
          authorName: g.sellerName || '校园用户',
          avatar: g.sellerAvatar || '',
          schoolId: g.schoolId || '',
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
          viewText: fmtCount(g.viewCount),
          imageList: resolveList(g.imageList),
          categoryName: g.categoryName || '',
          liked: !!g.liked,
          likeCount: g.likeCount || 0,
          likeText: fmtCount(g.likeCount),
          commentCount: 0,
          commentText: fmtCount(0)
        }
      })

      // 帖子与闲置按发布时间混排，形成信息流
      const feed = posts.concat(goods).sort(function (a, b) {
        return (b.createdAt || '').localeCompare(a.createdAt || '')
      })

      // 同市浏览范围下，标记同校 / 同市（异校）：同校帖子打绿标「同校」，其余同城异校打蓝标「同市」
      const me = self.data.user || (getApp().globalData && getApp().globalData.user) || mp.getStorageSync('user') || {}
      const mySchoolId = me.schoolId
      const isCity = self.data.scopeMode === 'city'
      feed.forEach(function (item) {
        item.scopeBadge = ''
        if (!isCity || !mySchoolId || !item.schoolId) return
        item.scopeBadge = String(mySchoolId) === String(item.schoolId) ? '同校' : '同市'
      })

      self.setData({
        channels: channels,
        categories: categories,
        feed: feed,
        loading: false,
        loadError: okCount === 0,
        errorMsg: okCount === 0 ? (firstError || '加载失败，请检查后端服务') : ''
      })

      // 最热帖子：点赞 > 10 才值得占首页位
      const hotRes = res[4] || {}
      const hot = (hotRes.records || [])[0]
      if (!hot || (hot.likeCount || 0) <= 10) {
        self.setData({ hotPost: null, hotComment: null })
        return
      }
      const hotPost = {
        id: hot.id,
        authorId: hot.userId,
        authorName: hot.authorName || '校园用户',
        avatar: hot.authorAvatar || '',
        timeText: timeAgo(hot.createdAt),
        badge: hot.channelName || '校园频道',
        title: hot.title || '',
        excerpt: (hot.content || '').replace(/\s+/g, ' '),
        imageList: resolveList(hot.imageList),
        liked: !!hot.liked,
        likeCount: hot.likeCount || 0,
        likeText: fmtCount(hot.likeCount),
        commentCount: hot.commentCount || 0,
        commentText: fmtCount(hot.commentCount),
        viewCount: hot.viewCount || 0,
        viewText: fmtCount(hot.viewCount)
      }
      self.setData({ hotPost: hotPost })
      // 热评预览：取该帖第一条评论
      http.get('/posts/' + hot.id + '/comments', null, { silent: true }).then(function (list) {
        const c = (list || []).filter(function (x) {
          return !x.parentId
        })[0]
        if (!c) return
        self.setData({
          hotComment: {
            userName: c.userName || '校园用户',
            content: (c.content || '').replace(/\s+/g, ' ')
          }
        })
      }).catch(function () {})
    }).then(function () {
      mp.stopPullDownRefresh()
    })
  },

  /* ---------- 顶部导航 ---------- */

  /** 打开「浏览范围」弹层：本校 / 本市（+ 位置 API 解析出的定位城市） */
  openScope: function () {
    const self = this
    const user = getApp().globalData.user
    // 还没落学校：胶囊点了直接进「选学校」，此时本校/本市切换没有意义
    if (!user || !user.schoolId) {
      this.setData({ onboardShow: true })
      return
    }
    this.setData({
      scopeShow: true,
      schoolCity: (user && user.schoolCity) || ''
    })
    // 位置信息 API：静默定位，失败不打扰（弹层里只显示学校所在市）
    scopeMod.resolveGeoCity().then(function (geo) {
      self.setData({ geoCity: geo })
    })
  },

  closeScope: function () {
    this.setData({ scopeShow: false })
  },

  onScopeSelect: function (e) {
    const d = e.detail || {}
    const next = scopeMod.write({ scope: d.scope, city: d.city })
    this.setData({ scopeShow: false })
    this.applyUser(getApp().globalData.user)
    // 范围变了重新拉信息流
    this.setData({ feed: [], loading: true })
    this.loadData()
    if (next.scope === 'city') {
      toast('已切换到' + (next.city || '本市') + ' · 全市')
    }
  },

  /** 弹层里的「学籍认证 / 切换学校」 */
  onScopeVerify: function () {
    this.setData({ scopeShow: false })
    this.goStatus()
  },

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
      patch['feed[' + index + '].likeText'] = fmtCount(Math.max(0, (item.likeCount || 0) + (liked ? 1 : -1)))
      self.setData(patch)
    }).catch(function () {})
  },

  /** 最热帖子卡片的点赞 */
  likeHot: function () {
    const self = this
    const hot = this.data.hotPost
    if (!hot) return
    auth.ensureLogin().then(function () {
      return http.post('/posts/' + hot.id + '/like')
    }).then(function (liked) {
      const count = Math.max(0, (hot.likeCount || 0) + (liked ? 1 : -1))
      self.setData({
        'hotPost.liked': liked,
        'hotPost.likeCount': count,
        'hotPost.likeText': fmtCount(count)
      })
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

  /** 作者行跳转：进对方个人主页（catchtap 已阻止冒泡到卡片详情跳转） */
  goUserSpace: function (e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    mp.navigateTo({ url: '/pages/user/space/space?id=' + id })
  },

  /**
   * 首页「+ 关注」：真实关注 / 取消（乐观更新 + 防重复点击）。
   * 列表无批量关注状态接口，关注态仅维护在本页 followedAuthors 内存中，
   * 下拉刷新/重进页面会重置（设计文档 T05 允许列表态不持久化）。
   */
  onFollow: function (e) {
    const authorId = e.currentTarget.dataset.id
    if (!authorId) return
    const self = this
    const cur = this.data.followedAuthors[authorId] || { following: false, loading: false }
    if (cur.loading) return
    const next = !cur.following
    const patch = {}
    patch['followedAuthors[' + authorId + ']'] = { following: next, loading: true }
    this.setData(patch)
    followMod.toggle(authorId, cur.following).then(function (res) {
      const map = self.data.followedAuthors
      map[authorId] = { following: !!res.following, loading: false }
      self.setData({ followedAuthors: map })
    }).catch(function () {
      // 回滚到操作前状态
      const map = self.data.followedAuthors
      map[authorId] = { following: cur.following, loading: false }
      self.setData({ followedAuthors: map })
      toast('操作失败，请重试')
    })
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

/** 计数美化：1200 → 1.2k，12345 → 1.2万 */
function fmtCount(n) {
  n = Number(n) || 0
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + '万'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}
