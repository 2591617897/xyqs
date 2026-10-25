const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const scopeMod = require('../../utils/scope')
const zoneMod = require('../../utils/zones')

/**
 * 首页 = 功能区首页（不再用「频道 tab」）。
 *
 * 结构自上而下：
 *   1. 顶部栏：学校范围胶囊 / 搜索 / 通知
 *   2. 功能入口区（金刚区）：一排图标直达各功能
 *   3. 运营公告位
 *   4. 内容功能区：今日最热 / 我想说 / 校园闲置 / 校园公告 / 校园生活
 *
 * 各区配置集中在 utils/zones.js，加新功能不用动这里的导航逻辑。
 */
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
    /** 功能入口区（金刚区） */
    entries: [],
    /** 内容功能区 */
    zones: [],
    /** 闲置分类（校园闲置区的子筛选） */
    categories: [],
    loading: true,
    loadError: false,
    errorMsg: '',
    unread: 0
  },

  onLoad: function () {
    // 序号：快速滑动会连发多次加载，只认最后一次的结果（见 loadData 里的 seq 校验）
    this._loadSeq = 0
  },

  onShow: function () {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      // hidden:false 兜底：若上次弹层没走正常关闭路径，避免底栏一直不显示
      this.getTabBar().setData({ selected: 0, hidden: false })
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
    const cached = getApp().globalData.user
    // 未登录不主动发起登录：浏览是免登录的，登录必须先经用户确认（见 utils/auth.ensureLogin），
    // 这里只对「已经登录且还没落学校」的用户做引导
    if (!http.getToken()) return
    if (mp.getStorageSync('onboardSkipped')) return
    if (cached && cached.schoolId) return
    auth.ensureLogin().then(function (user) {
      if (!user || user.schoolId) return
      if (mp.getStorageSync('onboardSkipped')) return
      this.toggleTabBar(true)
      this.setData({ onboardShow: true })
    }.bind(this)).catch(function () {
      // 登录失败/用户拒绝都不阻塞浏览
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
      self.toggleTabBar(false)
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
    this.toggleTabBar(false)
  },

  /**
   * 弹层需要盖住自定义 tabBar。
   *
   * 自定义 tabBar 由框架层渲染（`custom-tab-bar/`），页面里的 z-index 再高也压不住它，
   * 官方给定的做法是由页面把它隐藏掉（`.tabbar-hidden`），否则弹层底部会被底栏切掉。
   */
  toggleTabBar: function (hide) {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ hidden: !!hide })
    }
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

  /**
   * 一次性拉齐首页所有功能区的数据。
   *
   * 各接口互不依赖，用 Promise.all 并行；每个请求各自 catch，
   * 否则一个失败会把整页吞掉（Promise.all 的失败会短路其它分支）。
   */
  loadData: function (refresh) {
    const self = this
    if (refresh) this.setData({ loading: true })
    // 序号：滑动/连点会并发多次请求，回来晚的旧结果不许覆盖新结果
    const seq = this._loadSeq = (this._loadSeq || 0) + 1

    let firstError = ''
    const fail = function (name) {
      return function (err) {
        if (!firstError && err && err.msg) firstError = err.msg
        console.error('[index] ' + name + ' 加载失败：', err)
        return null
      }
    }

    // 浏览范围：本校（默认）/ 本市（scope=city&city=xxx）
    const scopeParams = scopeMod.postParams()
    // 帖子一次拉够首页各区所需：公告区与生活区在前端按 channelId 拆分
    const postParams = Object.assign({ page: 1, size: 16 }, scopeParams)
    const hotParams = Object.assign({ page: 1, size: 1, sort: 'hot' }, scopeParams)
    const wallParams = Object.assign({ page: 1, size: 6 }, scopeParams)
    const goodsParams = Object.assign({ page: 1, size: 6, sort: 'latest' }, scopeMod.goodsParams())

    Promise.all([
      http.get('/channels', null, { silent: true }).catch(fail('channels')),
      http.get('/posts', postParams, { silent: true }).catch(fail('posts')),
      http.get('/posts', hotParams, { silent: true }).catch(fail('hot')),
      http.get('/wall/posts', wallParams, { silent: true }).catch(fail('wall')),
      http.get('/goods', goodsParams, { silent: true }).catch(fail('goods')),
      http.get('/goods/categories', null, { silent: true }).catch(fail('categories'))
    ]).then(function (res) {
      if (seq !== self._loadSeq) return // 已有更新的请求，丢弃本次结果
      const channels = res[0] || []
      // 判定依据只看「内容源」：帖子 / 最热 / 表白墙 / 闲置
      // 任一成功就能渲染出部分功能区（优雅降级）；
      // 全挂了才算整页失败，否则用户只会看到一句「暂时还没有内容」而不知道是服务挂了
      const contentOk = [res[1], res[2], res[3], res[4]].filter(function (x) {
        return !!x
      }).length

      const built = zoneMod.buildZones({
        posts: res[1] || {},
        hot: res[2] || {},
        wall: res[3] || {},
        goods: res[4] || {}
      }, channels)

      self.setData({
        entries: zoneMod.buildEntries(channels),
        categories: res[5] || [],
        zones: built,
        loading: false,
        loadError: contentOk === 0,
        errorMsg: contentOk === 0 ? (firstError || '加载失败，请检查后端服务') : ''
      })

      // 热评预览：今日最热区的第一条评论
      const hotIdx = built.findIndex(function (z) {
        return z.key === 'hot'
      })
      if (hotIdx < 0 || !built[hotIdx].items.length) return
      const hotId = built[hotIdx].items[0].id
      http.get('/posts/' + hotId + '/comments', null, { silent: true }).then(function (list) {
        const c = (list || []).filter(function (x) {
          return !x.parentId
        })[0]
        if (!c) return
        // setData 的路径键必须是变量，不能写成对象字面量的计算属性名
        const patch = {}
        patch['zones[' + hotIdx + '].items[0].hotComment'] = {
          userName: c.userName || '校园用户',
          content: (c.content || '').replace(/\s+/g, ' ')
        }
        self.setData(patch)
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
      this.toggleTabBar(true)
      this.setData({ onboardShow: true })
      return
    }
    this.toggleTabBar(true)
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
    this.toggleTabBar(false)
  },

  onScopeSelect: function (e) {
    const d = e.detail || {}
    const next = scopeMod.write({ scope: d.scope, city: d.city })
    this.setData({ scopeShow: false })
    this.toggleTabBar(false)
    this.applyUser(getApp().globalData.user)
    // 范围变了重新拉信息流
    this.setData({ zones: [], loading: true })
    this.loadData()
    if (next.scope === 'city') {
      toast('已切换到' + (next.city || '本市') + ' · 全市')
    }
  },

  /** 弹层里的「学籍认证 / 切换学校」 */
  onScopeVerify: function () {
    this.setData({ scopeShow: false })
    this.toggleTabBar(false)
    this.goStatus()
  },

  /* ---------- 功能区交互 ---------- */

  /** 金刚区入口 */
  goEntry: function (e) {
    navigate(e.currentTarget.dataset.url)
  },

  /** 功能区右上角「更多」 */
  goZoneMore: function (e) {
    navigate(e.currentTarget.dataset.url)
  },

  /** 功能区卡片 → 详情页（帖子 / 表白墙 / 闲置三种落点） */
  goZoneDetail: function (e) {
    const ds = e.currentTarget.dataset
    if (!ds.id) return
    if (ds.kind === 'wall') {
      mp.navigateTo({ url: '/pages/wall/detail?id=' + ds.id })
      return
    }
    if (ds.kind === 'goods') {
      mp.navigateTo({ url: '/pages/goods/detail?id=' + ds.id })
      return
    }
    mp.navigateTo({ url: '/pages/post/detail?id=' + ds.id })
  },

  /**
   * 功能区卡片点赞：不跳转，直接改状态。
   * 三个区的点赞接口不同（帖子 / 表白墙 / 闲置），按 kind 分流。
   */
  toggleZoneLike: function (e) {
    const self = this
    const ds = e.currentTarget.dataset
    const zi = ds.zi
    const ii = ds.ii
    const zone = (this.data.zones || [])[zi]
    if (!zone) return
    const item = (zone.items || [])[ii]
    if (!item) return
    auth.ensureLogin().then(function () {
      let url = '/posts/' + item.id + '/like'
      if (item.kind === 'wall') url = '/wall/posts/' + item.id + '/like'
      else if (item.kind === 'goods') url = '/goods/' + item.id + '/like'
      return http.post(url)
    }).then(function (liked) {
      const cnt = Math.max(0, (item.likeCount || 0) + (liked ? 1 : -1))
      const base = 'zones[' + zi + '].items[' + ii + ']'
      const patch = {}
      patch[base + '.liked'] = liked
      patch[base + '.likeCount'] = cnt
      patch[base + '.likeText'] = fmtCount(cnt)
      self.setData(patch)
    }).catch(function () {})
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

  /** 校园闲置区里的分类筛选 → 跳二手广场并带上分类 */
  goCategory: function (e) {
    this.openGoodsTab('', e.currentTarget.dataset.id)
  },

  /** 公告位：跳蚤集市 → 闲置广场 */
  onAnnounce: function () {
    this.openGoodsTab('')
  },

  /** 铃铛 → 私信（已是 tab 页，必须 switchTab，navigateTo 会被框架拒绝） */
  goMessages: function () {
    mp.switchTab({ url: '/pages/messages/index' })
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

  /** 作者行跳转：进对方个人主页（catchtap 已阻止冒泡到卡片详情跳转） */
  goUserSpace: function (e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    mp.navigateTo({ url: '/pages/user/space/space?id=' + id })
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

/** 底部 tabBar 的页面路径：这些页必须 switchTab，不能 navigateTo */
const TAB_PAGES = [
  '/pages/index/index',
  '/pages/goods/list',
  '/pages/messages/index',
  '/pages/user/index'
]

/**
 * 统一跳转：tabBar 页面用 switchTab（带参数会被丢弃），其余用 navigateTo。
 */
function navigate(url) {
  if (!url) return
  const path = url.split('?')[0]
  if (TAB_PAGES.indexOf(path) >= 0) {
    mp.switchTab({ url: path })
    return
  }
  mp.navigateTo({ url: url })
}
