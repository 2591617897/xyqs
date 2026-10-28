const http = require('../../../utils/request')
const { mp, toast } = require('../../../utils/util')
const auth = require('../../../utils/auth')
const scopeMod = require('../../../utils/scope')
const privacy = require('../../../utils/privacy')

/**
 * 账号与服务（我的页「账户与服务」整组下沉到此子页面）
 *
 * 把原来堆在「我的」主页面的 10 条菜单按使用场景分三组：
 *   账号与安全（认证 / 范围 / 隐私授权）、消息与隐私、帮助与关于。
 * 主页面只留一个入口卡跳到这里，主包不再被长菜单拖累。
 */
Page({
  data: {
    user: null,
    verifyStatus: 0,
    // 浏览范围（本校 / 本市），与首页共用同一份存储
    scopeLabel: '选择学校',
    scopeMode: 'school',
    scopeCity: '',
    schoolCity: '',
    scopeShow: false,
    geoCity: null
  },

  onLoad: function () {
    mp.setNavigationBarTitle({ title: '账号与服务' })
    this.refresh()
  },

  onShow: function () {
    // 从子页（如浏览范围选完返回、认证后）回来时刷新状态
    this.refresh()
  },

  refresh: function () {
    const cached = getApp().globalData.user
    const hasToken = !!http.getToken()
    this.setData({ user: hasToken ? cached : null })
    this.applyScope()
    if (hasToken) this.loadMe()
  },

  /** 拉取 verifyStatus 与缓存中的用户，供认证状态文案展示 */
  loadMe: function () {
    const self = this
    http.get('/user/me', null, { silent: true }).then(function (me) {
      self.setData({ user: me, verifyStatus: me.verifyStatus || 0 })
      self.applyScope()
    }).catch(function (err) {
      if (err && err.code === 404 && !self._reloginTried) {
        self._reloginTried = true
        auth.relogin().then(function (u) {
          self.setData({ user: u })
          self.loadMe()
        }).catch(function () {})
      }
    })
  },

  /** 胶囊文案：本校显示学校名，本市显示「城市 · 全市」 */
  applyScope: function () {
    const user = this.data.user || getApp().globalData.user
    const s = (user && user.schoolId) ? scopeMod.read() : { scope: 'school', city: '' }
    this.setData({
      schoolCity: (user && user.schoolCity) || '',
      scopeMode: s.scope,
      scopeCity: s.scope === 'city' ? s.city : '',
      scopeLabel: scopeMod.label(user && user.schoolName, user && user.schoolCity)
    })
  },

  /** 统一分发：每个菜单项只带 data-key，避免写 10 个几乎一样的方法 */
  go: function (e) {
    const key = e.currentTarget.dataset.key
    switch (key) {
      case 'verify': return this.goVerify()
      case 'scope': return this.openScope()
      case 'privacy-setting': return this.goPrivacySetting()
      case 'notify': return mp.navigateTo({ url: '/subpackages/msg/settings/index' })
      case 'privacy': return mp.navigateTo({ url: '/pages/legal/privacy' })
      case 'feedback': return this.goFeedback()
      case 'agreement': return mp.navigateTo({ url: '/pages/legal/agreement' })
      case 'rule': return mp.navigateTo({ url: '/pages/legal/rule' })
      case 'disclaimer': return mp.navigateTo({ url: '/pages/legal/disclaimer' })
      case 'about': return this.about()
    }
  },

  goVerify: function () {
    if (!http.getToken()) {
      this.login()
      return
    }
    mp.navigateTo({ url: '/pages/user/verify' })
  },

  /** 打开「浏览范围」弹层 */
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
    this.goVerify()
  },

  /** 隐私设置：查隐私授权状态并跳微信托管的隐私协议页 */
  goPrivacySetting: function () {
    const self = this
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
          confirmColor: '#1E5FA8',
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

  goFeedback: function () {
    auth.ensureLogin().then(function () {
      mp.navigateTo({ url: '/pages/report/report?type=feedback' })
    }).catch(function () {})
  },

  about: function () {
    mp.showModal({
      title: '校聚一城',
      content: '各校相聚，同城相连。\n\n这里可以逛本校圈子、在树洞匿名闲谈、看社团招新、交换闲置好物。\n\n注意：本站只做信息展示，不提供线上交易、支付与担保。闲置一律校内当面看货当面结清，请勿向任何人提前转账。',
      showCancel: false,
      confirmText: '知道啦'
    })
  },

  login: function () {
    const self = this
    toast('正在登录…')
    auth.ensureLogin(true).then(function (user) {
      self.setData({ user: user })
      self.loadMe()
      toast('登录成功')
    }).catch(function (err) {
      console.error('[login] 失败', err)
      toast(auth.loginErrorText(err))
    })
  }
})
