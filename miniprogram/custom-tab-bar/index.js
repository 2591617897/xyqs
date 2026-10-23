const { mp } = require('../utils/util')
const auth = require('../utils/auth')

Component({
  options: {
    addGlobalClass: true
  },

  data: {
    selected: 0,
    list: [
      { key: 'home', pagePath: '/pages/index/index', text: '首页', icon: '🏠' },
      { key: 'goods', pagePath: '/pages/goods/list', text: '二手', icon: '🏷️' },
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

    /** 中间发布按钮：选择发布类型 */
    onPlus: function () {
      auth.ensureVerified().then(function () {
        mp.showActionSheet({
          itemList: ['发布闲置', '发帖子', '写匿名心事'],
          success: function (res) {
            if (res.tapIndex === 0) mp.navigateTo({ url: '/pages/goods/publish' })
            else if (res.tapIndex === 1) mp.navigateTo({ url: '/pages/post/publish' })
            else if (res.tapIndex === 2) mp.switchTab({ url: '/pages/wall/index' })
          },
          fail: function () {}
        })
      }).catch(function () {})
    }
  }
})
