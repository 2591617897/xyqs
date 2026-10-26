const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const scopeMod = require('../../utils/scope')
const zoneMod = require('../../utils/zones')
const adMod = require('../../utils/ad')

/**
 * 首页 = 功能区首页（不再用「频道 tab」）。
 *
 * 结构自上而下：
 *   1. 顶部范围行（默认不渲染，仅未绑学校/本市模式时出现，见 scopeKindOf）
 *   2. 运营公告位
 *   3. 运营 Banner
 *   4. 内容功能区：今日最热 / 我想说 / 校园闲置 / 校园公告 / 校园生活
 *
 * 功能入口区（原顶部金刚区）已独立成 `pages/zone/index`（底部「功能区」tab），
 * 首页不再放功能选择，避免与底栏重复。入口配置仍只在 utils/zones.js#buildEntries 一处。
 */
Page({
  data: {
    user: null,
    // 浏览范围（本校 / 本市）
    scopeLabel: '选择学校',
    scopeMode: 'school',
    scopeCity: '',
    schoolCity: '',
    /** 顶部范围行的形态：'' 不渲染 / 'onboard' 未绑学校 / 'city' 本市（见 scopeKindOf） */
    scopeKind: '',
    scopeShow: false,
    geoCity: null,
    /** 新用户选学校引导 */
    onboardShow: false,
    onboardSaving: false,
    /** 内容功能区（功能入口已独立成「功能区」tab 页，首页不再持有 entries） */
    zones: [],
    /** 闲置分类（校园闲置区的子筛选） */
    categories: [],
    /**
     * 首页 Banner：运营在后台「广告位管理」配置，按 code 拉取。
     * 空数组是正常的（没投广告 / 未过审 / 定向没命中），此时整块不渲染。
     */
    banners: [],
    /**
     * 后台公告（运营在「通知公告」发布）。
     * 有则替换掉默认的「跳蚤集市」运营位；为空时保留默认卡片，保证首屏不空缺。
     */
    notices: [],
    loading: true,
    loadError: false,
    errorMsg: ''
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
    this.checkOnboard()
  },

  /** 用最新用户信息刷新范围行文案与展示 */
  applyUser: function (user) {
    // 没有学校时「本市」拿不到城市，没有意义 → 一律按「选择学校」展示，
    // 避免残留的 feedScope=city 把范围行显示成「某市 · 全市」
    const s = (user && user.schoolId) ? scopeMod.read() : { scope: 'school', city: '' }
    this.setData({
      user: user,
      schoolCity: (user && user.schoolCity) || '',
      scopeMode: s.scope,
      scopeCity: s.scope === 'city' ? s.city : '',
      scopeLabel: scopeMod.label(user && user.schoolName, user && user.schoolCity),
      scopeKind: this.scopeKindOf(user, s.scope)
    })
  },

  /**
   * 顶部范围行该不该出现、以哪种形态出现。
   *
   * 只有两种情况值得占用一行：
   *   'onboard' 还没绑学校 —— 后端本校筛选此时不生效（schoolId 为空＝不过滤），
   *             不提示的话新用户会以为自己看的就是本校内容；
   *   'city'    已切到本市 —— 必须让用户看到当前不在本校，并能一键切回。
   * 其余情况（已绑学校 + 本校模式）返回空串，顶部整块不渲染。
   */
  scopeKindOf: function (user, scope) {
    if (!user || !user.schoolId) return 'onboard'
    return scope === 'city' ? 'city' : ''
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
      // 未登录：保持「选择学校」引导（游客也是本校筛选失效的状态，需要提示）
      this.setData({ scopeLabel: '选择学校', scopeKind: 'onboard' })
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
          self.setData({ user: null, scopeLabel: '选择学校', scopeKind: 'onboard' })
        })
        return
      }
      // 其余静默失败：保留缓存里的学校名，不打扰首页
    })
  },

  /* 顶部铃铛的未读角标已随铃铛一起移除（私信入口改由底栏 tab 承担，
     custom-tab-bar 自己的 unread 角标更准，这里不再重复拉 /messages/unread） */

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
    // Banner 的「城市定向」需要当前城市；未登录/未选学校时留空（那种情况只拿得到全国投放的广告）
    const cityParam = (getApp().globalData.user || {}).schoolCity || ''
    const goodsParams = Object.assign({ page: 1, size: 6, sort: 'latest' }, scopeMod.goodsParams())

    Promise.all([
      http.get('/channels', null, { silent: true }).catch(fail('channels')),
      http.get('/posts', postParams, { silent: true }).catch(fail('posts')),
      http.get('/posts', hotParams, { silent: true }).catch(fail('hot')),
      http.get('/wall/posts', wallParams, { silent: true }).catch(fail('wall')),
      http.get('/goods', goodsParams, { silent: true }).catch(fail('goods')),
      http.get('/goods/categories', null, { silent: true }).catch(fail('categories')),
      // 运营位：Banners 与公告。这两个是「锦上添花」，失败不影响功能区渲染，
      // 所以不进 contentOk 的判定（否则后台没配广告时首页会被误判成整页加载失败）
      http.get('/ads/home_banner', { city: cityParam }, { silent: true }).catch(fail('banners')),
      http.get('/notices', null, { silent: true }).catch(fail('notices'))
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

      const banners = res[6] || []
      const notices = res[7] || []
      self.setData({
        categories: res[5] || [],
        banners: banners,
        notices: notices,
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

  /**
   * 本市模式下的一键「切回本校」。
   *
   * 顶部范围行出现时就是为了让用户能马上改回来，所以不再走一遍弹层，
   * 直接写 feedScope + 重新拉流；城市参数随之清掉（scope=school 时后端不看 city）。
   */
  switchToSchool: function () {
    scopeMod.write({ scope: 'school' })
    this.applyUser(getApp().globalData.user)
    this.setData({ zones: [], loading: true })
    this.loadData()
    toast('已切回本校')
  },

  /** 弹层里的「学籍认证 / 切换学校」 */
  onScopeVerify: function () {
    this.setData({ scopeShow: false })
    this.toggleTabBar(false)
    this.goStatus()
  },

  /* ---------- 功能区交互 ---------- */

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

  /**
   * Banner 图片加载失败：把这条从列表里摘掉，避免留一个灰块占位。
   * 常见原因是素材图被删或用了局域网地址（真机 <image> 加载 http://192.168.x.x 会静默失败）。
   */
  onBannerError: function (e) {
    const idx = e.currentTarget.dataset.index
    if (idx == null) return
    const list = (this.data.banners || []).slice()
    if (idx < 0 || idx >= list.length) return
    list.splice(idx, 1)
    this.setData({ banners: list })
  },

  /**
   * 点击 Banner：上报点击数后按 linkType 跳目标。
   * 跳转分支统一在 utils/ad.js（与其它投放位共用一份，避免各页面行为不一致）。
   */
  onAdTap: function (e) {
    const ad = e.currentTarget.dataset.ad
    if (!ad) return
    adMod.reportClick(ad.id)
    adMod.open(ad)
  },

  /** 公告：跳详情（目前公告详情页复用 legal 风格的通用页，先用 toast 兜住正文摘要） */
  onNoticeTap: function (e) {
    const n = e.currentTarget.dataset.notice
    if (!n) return
    mp.navigateTo({
      url: '/pages/notice/index?id=' + n.id,
      fail: function () {
        // 详情页缺失时至少让用户看到内容，不至于点了没反应
        mp.setClipboardData({ data: (n.title || '') + '\n' + (n.content || '') })
      }
    })
  },

  /**
   * 打开闲置广场。
   * 「闲置」已不在底栏（让位给「功能区」），goods/list 不再是 tab 页 → 走 navigateTo。
   * 展示模式（全部/我的闲置/我的收藏）仍靠 globalData.goodsMode 传递。
   */
  openGoods: function (mode, categoryId) {
    const app = getApp()
    if (app.globalData) app.globalData.goodsMode = mode || ''
    if (categoryId) mp.setStorageSync('pendingCategory', categoryId)
    mp.navigateTo({ url: '/pages/goods/list' })
  },

  /** 校园闲置区里的分类筛选 → 跳二手广场并带上分类 */
  goCategory: function (e) {
    this.openGoods('', e.currentTarget.dataset.id)
  },

  /** 公告位：跳蚤集市 → 闲置广场 */
  onAnnounce: function () {
    this.openGoods('')
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
      title: '校园聚 · 校园闲置与圈子社区',
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
  '/pages/zone/index',
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
