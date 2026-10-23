const { mp } = require('../utils/util')
const auth = require('../utils/auth')

Component({
  options: {
    addGlobalClass: true
  },

  data: {
    selected: 0,
    showSheet: false,
    hidden: false,
    list: [
      { key: 'home', pagePath: '/pages/index/index', text: '首页', icon: '🏠' },
      { key: 'goods', pagePath: '/pages/goods/list', text: '二手闲置', icon: '🏷️' },
      { key: 'plus', pagePath: '', text: '', icon: '', plus: true },
      { key: 'wall', pagePath: '/pages/wall/index', text: '表白墙', icon: '💌' },
      { key: 'mine', pagePath: '/pages/user/index', text: '我的', icon: '👤' }
    ]
  },

  methods: {
    onTap: function (e) {
      const self = this
      const index = Number(e.currentTarget.dataset.index)
      const item = this.data.list[index]
      if (!item || item.plus) return
      if (index === this.data.selected) return

      // 点底栏「二手」视为回到全部闲置，清掉"我的闲置/收藏"模式
      if (item.pagePath === '/pages/goods/list') {
        const app = getApp()
        if (app && app.globalData) app.globalData.goodsMode = ''
      }

      mp.switchTab({
        url: item.pagePath,
        success: function () {
          self.setData({ selected: index })
        }
      })
    },

    /** 中间发布按钮：弹出彩色发布面板 */
    onPlus: function () {
      this.setData({ showSheet: true })
    },

    closeSheet: function () {
      this.setData({ showSheet: false })
    },

    stop: function () {},

    /** 发布面板：需校园认证的发布类入口 */
    goPage: function (e) {
      const self = this
      const url = e.currentTarget.dataset.url
      if (!url) return
      auth.ensureVerified().then(function () {
        self.setData({ showSheet: false })
        mp.navigateTo({ url: url })
      }).catch(function () {})
    },

    /** 发布面板：tab 页入口，无需认证 */
    goTab: function (e) {
      const url = e.currentTarget.dataset.url
      if (!url) return
      this.setData({ showSheet: false })
      mp.switchTab({ url: url })
    }
  }
})
