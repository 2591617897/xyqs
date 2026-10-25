const { mp } = require('../utils/util')
const auth = require('../utils/auth')
const http = require('../utils/request')

Component({
  options: {
    addGlobalClass: true
  },

  data: {
    selected: 0,
    showSheet: false,
    hidden: false,
    /** 私信未读总数：显示在「私信」tab 右上角 */
    unread: 0,
    list: [
      { key: 'home', pagePath: '/pages/index/index', text: '发现', icon: '🏠' },
      { key: 'zone', pagePath: '/pages/zone/index', text: '功能', icon: '🧩' },
      { key: 'plus', pagePath: '', text: '', icon: '', plus: true },
      { key: 'message', pagePath: '/pages/messages/index', text: '消息', icon: '💬' },
      { key: 'mine', pagePath: '/pages/user/index', text: '我的', icon: '👤' }
    ]
  },

  lifetimes: {
    attached: function () {
      this.refreshUnread()
    }
  },

  // 自定义 tabBar 由框架挂在每个 tab 页下，切 tab 时靠这个回调刷新角标
  pageLifetimes: {
    show: function () {
      this.refreshUnread()
    }
  },

  methods: {
    onTap: function (e) {
      const self = this
      const index = Number(e.currentTarget.dataset.index)
      const item = this.data.list[index]
      if (!item || item.plus) return
      if (index === this.data.selected) return

      // 说明：「闲置」已从底栏移到「功能区」页里，本组件不再需要清 goodsMode
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
    },

    /**
     * 发布面板：普通页面入口，无需认证。
     * 与 goPage 的区别：不强制学籍认证（表白墙是匿名的，游客也要能进）。
     */
    goNav: function (e) {
      const url = e.currentTarget.dataset.url
      if (!url) return
      this.setData({ showSheet: false })
      mp.navigateTo({ url: url })
    },

    /**
     * 私信 tab 的未读角标。
     * 未登录时直接置 0 —— 游客浏览是设计，不能因为一个角标去触发登录。
     */
    refreshUnread: function () {
      const self = this
      if (!http.getToken()) {
        if (this.data.unread) this.setData({ unread: 0 })
        return
      }
      http.get('/messages/unread', null, { silent: true }).then(function (res) {
        self.setData({ unread: (res && res.unread) || 0 })
      }).catch(function () {})
    }
  }
})
