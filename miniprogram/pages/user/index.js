const http = require('../../utils/request')
const { mp, toast, timeAgo } = require('../../utils/util')
const auth = require('../../utils/auth')
const mock = require('../../utils/mock')
const upload = require('../../utils/upload')

Page({
  data: {
    user: null,
    logged: false,
    nicknameDraft: '',
    stats: { goods: 0, favorites: 0, orders: 0 },
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
      user: cached,
      nicknameDraft: cached ? cached.nickname || '' : ''
    })
    if (http.getToken()) {
      this.loadMe()
      this.loadStats()
      this.loadOrders()
    } else {
      this.setData({ loading: false })
    }
  },

  loadMe: function () {
    const self = this
    http.get('/user/me', null, { silent: true }).then(function (me) {
      self.setData({
        user: me,
        logged: true,
        loading: false,
        nicknameDraft: me.nickname || ''
      })
      auth.updateUser({
        id: me.id,
        nickname: me.nickname,
        avatar: me.avatar,
        schoolId: me.schoolId,
        verifyStatus: me.verifyStatus
      })
    }).catch(function (err) {
      // 只有 401 才能判定为未登录；网络异常时保留登录态
      if (err && err.code === 401) {
        self.setData({ loading: false, logged: false })
      } else {
        self.setData({ loading: false })
      }
    })
  },

  loadStats: function () {
    const self = this
    http.get('/goods/mine', null, { silent: true }).then(function (list) {
      self.setData({ 'stats.goods': (list || []).length })
    }).catch(function () {})
    http.get('/goods/favorites', null, { silent: true }).then(function (list) {
      self.setData({ 'stats.favorites': (list || []).length })
    }).catch(function () {})
  },

  loadOrders: function () {
    const self = this
    http.get('/goods/orders/mine', { role: 'buyer' }, { silent: true }).then(function (list) {
      const buyer = (list || []).map(decorateOrder)
      self.setData({ buyerOrders: buyer, 'stats.orders': buyer.length })
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
      self.loadStats()
      self.loadOrders()
      toast('登录成功')
    }).catch(function () {
      toast('登录失败，请重试')
    })
  },

  /**
   * 微信取头像：用户主动点击 open-type="chooseAvatar" 按钮
   * 拿到的是临时路径，必须先上传换成可访问的 URL 再入库
   */
  onChooseAvatar: function (e) {
    const self = this
    if (!this.data.logged) {
      this.login()
      return
    }
    const tmpPath = e && e.detail && e.detail.avatarUrl
    if (!tmpPath) {
      toast('未获取到头像，请重试')
      return
    }
    mp.showLoading({ title: '上传中', mask: true })
    upload.uploadImage(tmpPath).then(function (url) {
      mp.hideLoading()
      return http.put('/user/me', { avatar: url }, { loading: '保存中' })
    }).then(function (me) {
      self.applyMe(me)
      toast('头像已更新')
    }).catch(function () {
      mp.hideLoading()
    })
  },

  /** 昵称：type="nickname" 由微信输入法给出候选，用户也可自己输入 */
  onNicknameInput: function (e) {
    this.setData({ nicknameDraft: e.detail.value })
  },

  saveNickname: function () {
    const self = this
    const nickname = (this.data.nicknameDraft || '').trim()
    if (!this.data.logged || !nickname) return
    if (this.data.user && nickname === this.data.user.nickname) return
    http.put('/user/me', { nickname: nickname }, { loading: '保存中' }).then(function (me) {
      self.applyMe(me)
      toast('昵称已保存')
    }).catch(function () {
      // 失败时回滚输入框，避免界面与服务端不一致
      self.setData({ nicknameDraft: (self.data.user && self.data.user.nickname) || '' })
    })
  },

  /** 统一同步服务端用户信息到页面与本地缓存 */
  applyMe: function (me) {
    this.setData({ user: me, logged: true, nicknameDraft: me.nickname || '' })
    auth.updateUser({
      id: me.id,
      nickname: me.nickname,
      avatar: me.avatar,
      schoolId: me.schoolId,
      verifyStatus: me.verifyStatus
    })
  },

  /** 兜底：从相册选一张作为头像（走已有的上传接口） */
  chooseAvatar: function () {
    const self = this
    if (!this.data.logged) {
      this.login()
      return
    }
    mp.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      success: function (res) {
        const path = res.tempFilePaths && res.tempFilePaths[0]
        if (!path) return
        mp.showLoading({ title: '上传中', mask: true })
        upload.uploadImage(path).then(function (url) {
          mp.hideLoading()
          return http.put('/user/me', { avatar: url }, { loading: '保存中' })
        }).then(function (me) {
          self.applyMe(me)
          toast('头像已更新')
        }).catch(function () {
          mp.hideLoading()
        })
      }
    })
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
          nicknameDraft: '',
          stats: { goods: 0, favorites: 0, orders: 0 },
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
