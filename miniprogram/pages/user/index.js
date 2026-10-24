const http = require('../../utils/request')
const { mp, toast, timeAgo } = require('../../utils/util')
const auth = require('../../utils/auth')
const scopeMod = require('../../utils/scope')
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
    mockMode: http.USE_MOCK
  },

  onShow: function () {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 4 })
    }
    mp.setNavigationBarTitle({ title: '我的' })
    const cached = getApp().globalData.user
    this.setData({
      logged: !!http.getToken(),
      user: cached
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
    const s = scopeMod.read()
    const user = this.data.user || getApp().globalData.user
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
  },

  onScopeSelect: function (e) {
    const d = e.detail || {}
    const next = scopeMod.write({ scope: d.scope, city: d.city })
    this.setData({ scopeShow: false })
    this.applyScope()
    if (next.scope === 'city') {
      toast('已切换到' + (next.city || '本市') + ' · 全市')
    }
  },

  /** 弹层里的「学籍认证 / 切换学校」 */
  onScopeVerify: function () {
    this.setData({ scopeShow: false })
    if (typeof this.goVerify === 'function') this.goVerify()
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
    }).catch(function () {
      toast('登录失败，请重试')
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

  goMyGoods: function () {
    if (!this.guard()) return
    // tabBar 页面不能用 navigateTo，用全局标记传递展示模式
    getApp().globalData.goodsMode = 'mine'
    mp.switchTab({ url: '/pages/goods/list' })
  },

  goFavorites: function () {
    if (!this.guard()) return
    getApp().globalData.goodsMode = 'fav'
    mp.switchTab({ url: '/pages/goods/list' })
  },

  /**
   * 「我的关注」入口：P0 先跳自己的个人主页（isSelf 显示「编辑资料」），
   * P1 关注/粉丝列表页（pages/user/follows）上线后再替换。
   */
  goMyFollows: function () {
    if (!this.guard()) return
    const user = this.data.user || getApp().globalData.user
    if (!user || !user.id) {
      toast('还没有登录，请先登录')
      return
    }
    mp.navigateTo({ url: '/pages/user/space/space?id=' + user.id })
  },

  openOrders: function () {
    if (!this.guard()) return
    this.setData({ showOrders: true })
  },

  closeOrders: function () {
    this.setData({ showOrders: false })
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

  /** 消息中心（私信 / 赞与收藏 / 评论） */
  goMessages: function () {
    mp.navigateTo({ url: '/pages/messages/index' })
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

  goDisclaimer: function () {
    mp.navigateTo({ url: '/pages/legal/disclaimer' })
  },

  about: function () {
    mp.showModal({
      title: '校园趣事',
      content: '一个只属于同校同学的闲置流转与心事角落。发布内容需完成校园身份认证。',
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
