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

      const decorate = function (list) {
        return list.map(function (u) {
          return Object.assign({}, u, { schoolName: u.schoolId ? (schoolMap[u.schoolId] || '') : '' })
        })
      }
      self.followingList = decorate(following)
      self.fansList = decorate(fans)
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

  /** 关注 / 取消关注 */
  toggleFollow: function (e) {
    const idx = e.currentTarget.dataset.index
    const id = e.currentTarget.dataset.id
    const list = this.data.list
    const item = list[idx]
    if (!item) return

    // 不能关注自己
    const selfId = (getApp().globalData.user || {}).id
    if (String(id) === String(selfId)) {
      toast('这是你自己哦')
      return
    }

    const wasFollowing = item.isFollowing
    const self = this

    // 乐观更新
    item.isFollowing = !wasFollowing
    list[idx] = Object.assign({}, item)
    this.setData({ list: list })

    const url = '/user/follow/' + id
    const method = wasFollowing ? 'DELETE' : 'POST'
    request({ url: url, method: method, silent: true }).then(function (resp) {
      // 服务端确认，可能和乐观更新一致（幂等）
      list[idx].isFollowing = resp.following
      // 同步更新两个列表的缓存
      if (self.followingList) {
        const fIdx = self.followingList.findIndex(function (u) { return String(u.id) === String(id) })
        if (fIdx >= 0) self.followingList[fIdx].isFollowing = resp.following
      }
      if (self.fansList) {
        const fIdx = self.fansList.findIndex(function (u) { return String(u.id) === String(id) })
        if (fIdx >= 0) self.fansList[fIdx].isFollowing = resp.following
      }
      // 更新计数
      if (self.data.activeTab === 'following') {
        self.setData({
          list: list,
          followingCount: (self.followingList || []).length,
          fansCount: (self.fansList || []).length
        })
      } else {
        self.setData({
          list: list,
          followingCount: (self.followingList || []).length,
          fansCount: (self.fansList || []).length
        })
      }
    }).catch(function (err) {
      // 失败回滚
      list[idx].isFollowing = wasFollowing
      self.setData({ list: list })
      if (err && err.code !== 401) {
        toast(err.msg || '操作失败')
      }
    })
  }
})
