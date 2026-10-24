const { request } = require('../../../utils/request')
const { mp, toast } = require('../../../utils/util')

Page({
  data: {
    activeTab: 'following', // following | fans
    list: [],
    followingCount: 0,
    fansCount: 0,
    loading: true
  },

  onLoad: function (options) {
    const tab = options.tab === 'fans' ? 'fans' : 'following'
    this.setData({ activeTab: tab })
    this.loadAll()
  },

  onShow: function () {
    // 从其他页面回来（比如个人主页关注/取消了）刷新
    if (this.data.loading === false) {
      this.loadAll()
    }
  },

  /** 并行拉两个列表 + 学校字典，再展示当前 tab */
  loadAll: function () {
    const self = this
    this.setData({ loading: true })

    Promise.all([
      request({ url: '/user/follow/list?type=following', silent: true }),
      request({ url: '/user/follow/list?type=fans', silent: true }),
      request({ url: '/auth/schools', silent: true }).catch(function () { return [] })
    ]).then(function (results) {
      const following = results[0] || []
      const fans = results[1] || []
      const schools = results[2] || []

      // 学校 id→name 字典
      const schoolMap = {}
      schools.forEach(function (s) { schoolMap[s.id] = s.name })

      /**
       * 卡片装态：
       * - followedMe（对方是否关注我）：粉丝列表恒为 true；关注列表取后端 followedBy
       * - mutual（相互关注）= 我关注对方 && 对方关注我
       * 把「对方关注我」单独记住，取消关注再回关时互关状态才能还原，
       * 不用重拉接口（对方是否关注我并不会因为我的操作而改变）。
       */
      const decorate = function (list, isFansList) {
        return (list || []).map(function (u) {
          const following = !!u.isFollowing
          const followedMe = isFansList ? true : !!u.followedBy
          return Object.assign({}, u, {
            schoolName: u.schoolId ? (schoolMap[u.schoolId] || '') : '',
            isFollowing: following,
            followedMe: followedMe,
            mutual: following && followedMe
          })
        })
      }
      self.followingList = decorate(following, false)
      self.fansList = decorate(fans, true)
      self.applyCurrentTab()
    }).catch(function (err) {
      self.setData({ loading: false })
      if (err && err.code === 401) {
        toast('请先登录')
      }
    })
  },

  applyCurrentTab: function () {
    const list = this.data.activeTab === 'following' ? this.followingList : this.fansList
    this.setData({
      list: list,
      followingCount: (this.followingList || []).length,
      fansCount: (this.fansList || []).length,
      loading: false
    })
  },

  switchTab: function (e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.activeTab) return
    this.setData({ activeTab: tab })
    this.applyCurrentTab()
  },

  openProfile: function (e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    mp.navigateTo({ url: '/pages/user/space/space?id=' + id })
  },

  /** 关注 / 取消关注（乐观更新 + 失败回滚 + 防重复点击） */
  toggleFollow: function (e) {
    const idx = e.currentTarget.dataset.index
    const id = e.currentTarget.dataset.id
    const item = this.data.list[idx]
    if (!item || this.toggling) return

    // 不能关注自己
    const selfId = (getApp().globalData.user || {}).id
    if (String(id) === String(selfId)) {
      toast('这是你自己哦')
      return
    }

    const self = this
    const wasFollowing = !!item.isFollowing
    this.toggling = true

    /**
     * 按「我的关注状态」重算两个列表的缓存行，再套回当前 tab。
     * 返回全新数组（不原地改 this.data.list），避免 setData 拿到同一引用被判无变化。
     * mutual = 我关注对方 && 对方关注我（followedMe 是缓存里记住的另一半，不受本次操作影响）。
     */
    const applyFollowing = function (following) {
      const patch = function (arr) {
        return (arr || []).map(function (u) {
          if (String(u.id) !== String(id)) return u
          return Object.assign({}, u, {
            isFollowing: following,
            mutual: following && !!u.followedMe
          })
        })
      }
      self.followingList = patch(self.followingList)
      self.fansList = patch(self.fansList)
      self.applyCurrentTab()
    }

    // 乐观更新
    applyFollowing(!wasFollowing)

    const url = '/user/follow/' + id
    const method = wasFollowing ? 'DELETE' : 'POST'
    request({ url: url, method: method, silent: true }).then(function (resp) {
      // 以服务端确认结果为准（幂等，正常与乐观值一致）
      applyFollowing(!!resp.following)
      self.toggling = false
    }).catch(function (err) {
      // 失败回滚
      applyFollowing(wasFollowing)
      self.toggling = false
      if (err && err.code !== 401) {
        toast(err.msg || '操作失败')
      }
    })
  }
})
