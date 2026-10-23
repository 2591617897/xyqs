const http = require('../../utils/request')
const { timeAgo, priceText, mp, categoryEmoji } = require('../../utils/util')
const auth = require('../../utils/auth')

Page({
  data: {
    greeting: '你好',
    user: null,
    goods: [],
    posts: [],
    categories: [],
    loading: true,
    loadError: false
  },

  onShow: function () {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 })
    }
    this.setData({ user: getApp().globalData.user })
    this.setGreeting()
    this.loadData()
    mp.setNavigationBarTitle({ title: '校园趣事' })
  },

  onPullDownRefresh: function () {
    this.loadData(true)
  },

  retry: function () {
    this.loadData(true)
  },

  setGreeting: function () {
    const h = new Date().getHours()
    let g = '晚上好'
    if (h < 5) g = '夜深了'
    else if (h < 11) g = '早上好'
    else if (h < 14) g = '中午好'
    else if (h < 18) g = '下午好'
    this.setData({ greeting: g })
  },

  loadData: function (refresh) {
    const self = this
    if (refresh) this.setData({ loading: true })

    // 三个接口分别兜底：任一失败不影响其余模块渲染
    let firstError = ''
    const fail = function (name) {
      return function (err) {
        if (!firstError && err && err.msg) firstError = err.msg
        console.error('[index] ' + name + ' 加载失败：', err)
        return null
      }
    }

    Promise.all([
      http.get('/goods', { size: 4, sort: 'latest' }, { silent: true }).catch(fail('goods')),
      http.get('/posts', { size: 3 }, { silent: true }).catch(fail('posts')),
      http.get('/goods/categories', null, { silent: true }).catch(fail('categories'))
    ]).then(function (res) {
      const goodsRes = res[0] || { records: [] }
      const postsRes = res[1] || { records: [] }
      const okCount = res.filter(function (x) {
        return !!x
      }).length

      const goods = (goodsRes.records || []).map(function (item) {
        item.timeText = timeAgo(item.createdAt)
        item.priceText = priceText(item.price)
        item.emoji = categoryEmoji(item.categoryId)
        return item
      })
      const posts = (postsRes.records || []).map(function (item) {
        item.timeText = timeAgo(item.createdAt)
        item.avatarText = (item.authorName || '校').substring(0, 1)
        return item
      })

      self.setData({
        goods: goods,
        posts: posts,
        categories: res[2] || [],
        loading: false,
        loadError: okCount === 0,
        errorMsg: okCount === 0 ? (firstError || '加载失败，请检查后端服务') : ''
      })
    }).then(function () {
      mp.stopPullDownRefresh()
    })
  },

  goSearch: function () {
    this.openGoodsTab('')
  },

  /** 统一切到二手 tab，并指定展示模式 */
  openGoodsTab: function (mode, categoryId) {
    const app = getApp()
    if (app.globalData) app.globalData.goodsMode = mode || ''
    if (categoryId) mp.setStorageSync('pendingCategory', categoryId)
    mp.switchTab({ url: '/pages/goods/list' })
  },

  goStatus: function () {
    const self = this
    const user = getApp().globalData.user
    if (user && user.verifyStatus === 2) {
      mp.switchTab({ url: '/pages/user/index' })
      return
    }
    if (!user) {
      // 未登录：先静默登录，再看是否需要认证
      auth.ensureLogin().then(function (u) {
        self.setData({ user: u })
        if (u.verifyStatus === 2) {
          mp.switchTab({ url: '/pages/user/index' })
        } else {
          mp.navigateTo({ url: '/pages/user/verify' })
        }
      }).catch(function () {})
      return
    }
    mp.navigateTo({ url: '/pages/user/verify' })
  },

  goGoodsList: function (e) {
    const id = e.currentTarget.dataset.id
    this.openGoodsTab('', id ? Number(id) : 0)
  },

  goGoodsDetail: function (e) {
    mp.navigateTo({ url: '/pages/goods/detail?id=' + e.currentTarget.dataset.id })
  },

  goPublish: function () {
    auth.ensureVerified().then(function () {
      mp.navigateTo({ url: '/pages/goods/publish' })
    }).catch(function () {})
  },

  goWall: function () {
    mp.switchTab({ url: '/pages/wall/index' })
  },

  goPosts: function () {
    mp.navigateTo({ url: '/pages/post/list' })
  },

  goPostDetail: function (e) {
    mp.navigateTo({ url: '/pages/post/detail?id=' + e.currentTarget.dataset.id })
  }
})
