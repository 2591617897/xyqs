const { mp } = require('./utils/util')

App({
  globalData: {
    token: '',
    user: null,
    schoolId: null,
    // 二手 tab 的展示模式：'' 浏览 / 'mine' 我的闲置 / 'fav' 我的收藏
    goodsMode: ''
  },

  onLaunch: function () {
    this.globalData.token = mp.getStorageSync('token') || ''
    this.globalData.user = mp.getStorageSync('user') || null
    if (this.globalData.user) {
      this.globalData.schoolId = this.globalData.user.schoolId || null
    }
  },

  onShow: function () {
    // 认证状态可能在 verify 页变更，回到前台时同步一次缓存
    const user = mp.getStorageSync('user')
    if (user) {
      this.globalData.user = user
      this.globalData.schoolId = user.schoolId || null
    }
  }
})
