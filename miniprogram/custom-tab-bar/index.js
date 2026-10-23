const { mp } = require('../utils/util')

Component({
  options: {
    addGlobalClass: true
  },

  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/index/index', text: '发现', icon: '🏠' },
      { pagePath: '/pages/goods/list', text: '二手', icon: '🏷️' },
      { pagePath: '/pages/wall/index', text: '表白墙', icon: '💌' },
      { pagePath: '/pages/user/index', text: '我的', icon: '👤' }
    ]
  },

  methods: {
    switchTab: function (e) {
      const self = this
      const index = Number(e.currentTarget.dataset.index)
      const path = e.currentTarget.dataset.path
      if (index === this.data.selected) return

      // 点底栏「二手」视为回到全部闲置，清掉"我的闲置/收藏"模式
      if (path === '/pages/goods/list') {
        const app = getApp()
        if (app && app.globalData) app.globalData.goodsMode = ''
      }

      mp.switchTab({
        url: path,
        success: function () {
          self.setData({ selected: index })
        }
      })
    }
  }
})
