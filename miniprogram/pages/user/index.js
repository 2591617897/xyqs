const http = require('../../utils/request')
const { mp, toast, timeAgo } = require('../../utils/util')
const auth = require('../../utils/auth')
const scopeMod = require('../../utils/scope')
const privacy = require('../../utils/privacy')
const mock = require('../../utils/mock')

Page({
  data: {
    user: null,
    logged: false,
    // 浏览范围（本校 / 本市），与首页共用同一份存储
    scopeLabel: '选择学校',
    scopeMode: 'school',
    scopeCity: '',
    schoolCity: '',
    scopeShow: false,
    geoCity: null,
    /** 未绑定学校时的选择弹层（复用 components/school-onboard） */
    onboardShow: false,
    onboardSaving: false,
    // 信用等级文案（由 loadMe 根据 creditScore 计算）
    creditLevel: '',
    // 关注 / 粉丝数（来自 /user/me）
    fansCount: 0,
    followCount: 0,
    loading: true,
    showOrders: false,
    orderRole: 'buyer',
    buyerOrders: [],
    sellerOrders: [],
    mockMode: http.USE_MOCK,
    // 视差滚动偏移量（动态 style 绑定到 hero）
    heroOffset: 0,
    // 入场动画开关：页面渲染完成后开启，配合 CSS delay 形成波浪入场
    pageReady: false
  },

  /**
   * 页面初次渲染完成 —— 开启入场动画
   * 等一帧让页面结构先渲染出来，再添加 .animate-in
   * CSS 的 .delay-1 ~ .delay-6 已定义好渐进延迟，实现波浪式入场
   */
  onReady: function () {
    const self = this
    setTimeout(function () {
      self.setData({ pageReady: true })
    }, 50)
  },

  /**
   * 页面滚动 —— hero 区域视差效果
   * 只修改 transform 属性，GPU 加速，保持 60fps
   * 视差倍率 0.3，最大偏移 60px，避免过度偏移
   */
  onPageScroll: function (e) {
    const scrollTop = e.scrollTop
    const offset = Math.min(scrollTop * 0.3, 60)
    this.setData({ heroOffset: offset })
  },

  onShow: function () {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      // hidden:false 兜底：弹层没走正常关闭路径时，别让底栏一直不显示
      this.getTabBar().setData({ selected: 4, hidden: false })
    }
    mp.setNavigationBarTitle({ title: '我的' })
    const cached = getApp().globalData.user
    const hasToken = !!http.getToken()
    this.setData({
      logged: hasToken,
      // 没有 token 时不要拿缓存渲染，否则会显示上一轮账号的昵称/学校（清库后尤其明显）
      user: hasToken ? cached : null
    })
    this.applyScope()
    if (http.getToken()) {
      this.loadMe()
      this.loadOrders()
    } else {
      this.setData({ loading: false })
    }
  },

  /** 胶囊文案：本校显示学校名，本市显示「城市 · 全市」 */
  applyScope: function () {
    const user = this.data.user || getApp().globalData.user
    // 没有学校时忽略「本市」残留，直接显示「选择学校」
    const s = (user && user.schoolId) ? scopeMod.read() : { scope: 'school', city: '' }
    this.setData({
      schoolCity: (user && user.schoolCity) || '',
      scopeMode: s.scope,
      scopeCity: s.scope === 'city' ? s.city : '',
      scopeLabel: scopeMod.label(user && user.schoolName, user && user.schoolCity)
    })
  },

  /** 打开「浏览范围」弹层：本校 / 本市（+ 位置 API 解析出的定位城市） */
  openScope: function () {
    const self = this
    this.toggleTabBar(true)
    this.setData({
      scopeShow: true,
      schoolCity: (this.data.user && this.data.user.schoolCity) || ''
    })
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
    this.applyScope()
    if (next.scope === 'city') {
      toast('已切换到' + (next.city || '本市') + ' · 全市')
    }
  },

  /** 弹层里的「学籍认证 / 切换学校」 */
  onScopeVerify: function () {
    this.setData({ scopeShow: false })
    this.toggleTabBar(false)
    if (typeof this.goVerify === 'function') this.goVerify()
  },

  /**
   * 弹层需要盖住自定义 tabBar。
   *
   * 自定义 tabBar 由框架层渲染（`custom-tab-bar/`），页面 z-index 压不住它，
   * 只能由页面主动隐藏，否则弹层底部会被底栏切掉（选学校列表被挡住就是这样）。
   */
  toggleTabBar: function (hide) {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ hidden: !!hide })
    }
  },

  loadMe: function () {
    const self = this
    http.get('/user/me', null, { silent: true }).then(function (me) {
      self.setData({
        user: me,
        logged: true,
        loading: false,
        fansCount: me.fansCount || 0,
        followCount: me.followCount || 0,
        creditLevel: creditLevelText(me.creditScore)
      })
      auth.updateUser({
        id: me.id,
        nickname: me.nickname,
        avatar: me.avatar,
        schoolId: me.schoolId,
        schoolName: me.schoolName || '',
        schoolCity: me.schoolCity || '',
        verifyStatus: me.verifyStatus
      })
      self.applyScope()
    }).catch(function (err) {
      // 令牌指向的用户已不存在（清库/换环境）：重建会话后重拉，别停在幽灵数据上
      if (err && err.code === 404 && !self._reloginTried) {
        self._reloginTried = true
        auth.relogin().then(function (u) {
          self.setData({ logged: true, user: u })
          self.loadMe()
          self.loadOrders()
        }).catch(function () {
          self.setData({ loading: false, logged: false, user: null })
        })
        return
      }
      // 只有 401 才能判定为未登录；网络异常时保留登录态
      if (err && err.code === 401) {
        self.setData({ loading: false, logged: false })
      } else {
        self.setData({ loading: false })
      }
    })
  },

  loadOrders: function () {
    const self = this
    http.get('/goods/orders/mine', { role: 'buyer' }, { silent: true }).then(function (list) {
      self.setData({ buyerOrders: (list || []).map(decorateOrder) })
    }).catch(function () {})
    http.get('/goods/orders/mine', { role: 'seller' }, { silent: true }).then(function (list) {
      self.setData({ sellerOrders: (list || []).map(decorateOrder) })
    }).catch(function () {})
  },

  login: function () {
    const self = this
    toast('正在登录…')
    auth.ensureLogin(true).then(function (user) {
      self.setData({ logged: true, user: user })
      self.loadMe()
      self.loadOrders()
      toast('登录成功')
    }).catch(function (err) {
      // 别再吞成「登录失败，请重试」：后端 500 的具体原因（如缺字段、配置缺失）
      // 必须能看见，否则每次只能靠翻服务端日志定位
      console.error('[login] 失败', err)
      toast(auth.loginErrorText(err))
    })
  },

  /** 个人信息：查看与修改昵称/头像/性别/手机号/简介 */
  goProfile: function () {
    if (!this.guard()) return
    mp.navigateTo({ url: '/pages/user/profile' })
  },

  /** 统一同步服务端用户信息到页面与本地缓存 */
  applyMe: function (me) {
    this.setData({ user: me, logged: true })
    auth.updateUser({
      id: me.id,
      nickname: me.nickname,
      avatar: me.avatar,
      schoolId: me.schoolId,
      schoolName: me.schoolName || '',
      schoolCity: me.schoolCity || '',
      verifyStatus: me.verifyStatus
    })
    this.applyScope()
  },

  logout: function () {
    const self = this
    mp.showModal({
      title: '退出登录',
      content: '退出后需要重新登录才能发布内容',
      success: function (res) {
        if (!res.confirm) return
        http.cleanLogin()
        self.setData({
          logged: false,
          user: null,
          fansCount: 0,
          followCount: 0,
          buyerOrders: [],
          sellerOrders: []
        })
      }
    })
  },

  goVerify: function () {
    if (!this.data.logged) {
      this.login()
      return
    }
    mp.navigateTo({ url: '/pages/user/verify' })
  },

  /* ---------- 选学校（未绑定时可自行选择，无需学籍认证） ---------- */
  goPickSchool: function () {
    const self = this
    const user = this.data.user
    // 已绑定：交给「去认证」处理换校（认证后换校必须走材料审核）
    if (user && user.schoolId) {
      this.goVerify()
      return
    }
    if (!this.data.logged) {
      // 这里只是「去选学校」，不是用户主动点登录 → 走默认登录（先弹确认）
      auth.ensureLogin().then(function (u) {
        self.toggleTabBar(true)
        self.setData({ logged: true, user: u, onboardShow: true })
      }).catch(function () {
        // 用户拒绝登录时弹窗已表达，这里不再打扰
      })
      return
    }
    this.toggleTabBar(true)
    this.setData({ onboardShow: true })
  },

  onOnboardSelect: function (e) {
    const self = this
    const school = e.detail && e.detail.school
    if (!school || this.data.onboardSaving) return
    this.setData({ onboardSaving: true })
    auth.ensureLogin().then(function () {
      return http.put('/user/me', { schoolId: school.id })
    }).then(function (me) {
      // schoolId 写进了 JWT 快照，必须重签 token，否则本校筛选不生效
      return auth.ensureLogin(true).then(function () {
        return me
      })
    }).then(function (me) {
      auth.updateUser({
        id: me.id,
        schoolId: me.schoolId,
        schoolName: me.schoolName || school.name || '',
        schoolCity: me.schoolCity || school.city || '',
        verifyStatus: me.verifyStatus
      })
      self.setData({ onboardShow: false, onboardSaving: false })
      self.toggleTabBar(false)
      // 绑定学校后范围回到「本校」，避免胶囊继续显示上一条「某市 · 全市」
      scopeMod.write({ scope: 'school' })
      self.loadMe()
      self.applyScope()
      toast('已绑定 ' + (me.schoolName || school.name || '学校'))
    }).catch(function () {
      self.setData({ onboardSaving: false })
    })
  },

  onOnboardClose: function () {
    this.setData({ onboardShow: false })
    this.toggleTabBar(false)
  },

  /** 我发布的 → 分包子页面（subpackages/me），不再是「闲置列表 + mine 模式」 */
  goMyGoods: function () {
    if (!this.guard()) return
    mp.navigateTo({ url: '/subpackages/me/goods/mine' })
  },

  /** 已卖出 → 分包子页面：/goods/mine?status=2 的成交记录 */
  goSold: function () {
    if (!this.guard()) return
    mp.navigateTo({ url: '/subpackages/me/goods/sold' })
  },

  /** 赞与收藏 → 分包子页面（点过赞的闲置，可在此取消收藏） */
  goFavorites: function () {
    if (!this.guard()) return
    mp.navigateTo({ url: '/subpackages/me/favorite/index' })
  },

  /**
   * 「我的关注」入口：进入关注/粉丝列表页，默认展示关注 Tab。
   */
  goMyFollows: function () {
    if (!this.guard()) return
    mp.navigateTo({ url: '/pages/user/follow/index?tab=following' })
  },

  /**
   * 「我的动态」入口：帖子 + 树洞合并时间线（分包子页面）。
   */
  goMyDynamic: function () {
    if (!this.guard()) return
    mp.navigateTo({ url: '/subpackages/me/dynamic/index' })
  },

  openOrders: function () {
    if (!this.guard()) return
    // 自定义 tabBar 是独立渲染层，页面里的 z-index 压不住它 → 必须显式隐藏，
    // 否则弹层底部（意向单列表最后一行 / 安全区留白）会被底栏和中间发布钮盖住。
    this.toggleTabBar(true)
    this.setData({ showOrders: true })
  },

  closeOrders: function () {
    this.setData({ showOrders: false })
    this.toggleTabBar(false)
  },

  switchOrderRole: function (e) {
    this.setData({ orderRole: e.currentTarget.dataset.role })
  },

  goPublish: function () {
    if (!this.guard()) return
    auth.ensureVerified().then(function () {
      mp.navigateTo({ url: '/pages/goods/publish' })
    }).catch(function () {})
  },

  /** 私信（已是 tab 页，走 switchTab） */
  goMessages: function () {
    mp.switchTab({ url: '/pages/messages/index' })
  },

  /** 未实现的演示入口统一占位 */
  demoEntry: function (e) {
    toast('「' + (e.currentTarget.dataset.name || '该功能') + '」即将上线，敬请期待')
  },

  goAgreement: function () {
    mp.navigateTo({ url: '/pages/legal/agreement' })
  },

  goPrivacy: function () {
    mp.navigateTo({ url: '/pages/legal/privacy' })
  },

  goFeedback: function () {
    const self = this
    auth.ensureLogin().then(function () {
      mp.navigateTo({ url: '/pages/report/report?type=feedback' })
    }).catch(function () {})
  },

  /**
   * 隐私设置：查隐私授权状态并跳微信托管的隐私协议页。
   * 真正的授权动作由微信官方弹窗完成（app.json 已开 __usePrivacyCheck__，
   * 且有意不注册 onNeedPrivacyAuthorization），这里只做状态展示与协议查看。
   */
  goPrivacySetting: function () {
    privacy.setting().then(function (s) {
      if (!s.supported) {
        toast('当前微信版本较低，请升级微信后再试')
        return
      }
      if (s.needAuthorization) {
        mp.showModal({
          title: '隐私授权',
          content: '你还没有同意' + s.contractName + '。使用头像、相册等功能时，微信会弹窗征得你的同意。',
          confirmText: '查看协议',
          cancelText: '知道了',
          confirmColor: '#006C49',
          success: function (res) {
            if (res.confirm) privacy.openContract()
          }
        })
        return
      }
      privacy.openContract().then(function (ok) {
        if (!ok) toast('当前微信版本不支持查看隐私协议')
      })
    })
  },

  goDisclaimer: function () {
    mp.navigateTo({ url: '/pages/legal/disclaimer' })
  },

  /** 社区规范：UGC 平台必须让用户能随时看到发布规则 */
  goRule: function () {
    mp.navigateTo({ url: '/pages/legal/rule' })
  },

  about: function () {
    mp.showModal({
      title: '校聚一城',
      content: '各校相聚，同城相连。\n\n这里可以逛本校圈子、在树洞匿名闲谈、看社团招新、交换闲置好物。\n\n注意：本站只做信息展示，不提供线上交易、支付与担保。闲置一律校内当面看货当面结清，请勿向任何人提前转账。',
      showCancel: false,
      confirmText: '知道啦'
    })
  },

  /** 长按底部版本号：重置模拟数据（仅模拟模式可用） */
  resetMock: function () {
    const self = this
    if (!http.USE_MOCK) {
      toast('当前为真实接口模式')
      return
    }
    mp.showModal({
      title: '重置模拟数据',
      content: '会清空你发布的内容、收藏和意向单，恢复到初始演示数据。确定吗？',
      confirmColor: '#ff4d4f',
      success: function (res) {
        if (!res.confirm) return
        mock.reset()
        mp.removeStorageSync('token')
        mp.removeStorageSync('user')
        getApp().globalData.token = ''
        getApp().globalData.user = null
        toast('已重置，正在重新载入')
        self.login()
      }
    })
  },

  stop: function () {},

  guard: function () {
    if (!this.data.logged) {
      toast('请先登录')
      this.login()
      return false
    }
    return true
  }
})

const STATUS_TEXT = ['待卖家确认', '卖家已确认', '已完成', '已取消']

function decorateOrder(o) {
  o.timeText = timeAgo(o.createdAt)
  o.statusText = STATUS_TEXT[o.status] || '待处理'
  return o
}

/** 信用分 → 等级文案（与后端 creditScore 规则一致） */
function creditLevelText(score) {
  if (score == null) return '暂无'
  if (score >= 800) return '极好'
  if (score >= 700) return '良好'
  if (score >= 600) return '一般'
  return '待提升'
}
