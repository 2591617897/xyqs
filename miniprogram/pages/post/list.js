const http = require('../../utils/request')
const { timeAgo, mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const { resolveList } = require('../../utils/img')

Page({
  data: {
    channels: [],
    channelId: 0,
    list: [],
    page: 1,
    size: 10,
    hasMore: true,
    loading: false,
    loadError: false
  },

  onLoad: function () {
    mp.setNavigationBarTitle({ title: '校园频道' })
    this.loadChannels()
    this.load(true)
  },

  onShow: function () {
    if (mp.getStorageSync('needRefreshPosts')) {
      mp.removeStorageSync('needRefreshPosts')
      this.load(true)
    }
  },

  onPullDownRefresh: function () {
    this.load(true)
  },

  onReachBottom: function () {
    this.load(false)
  },

  loadChannels: function () {
    const self = this
    http.get('/channels', null, { silent: true }).then(function (list) {
      self.setData({ channels: list || [] })
    }).catch(function () {})
  },

  load: function (reset) {
    const self = this
    if (this.data.loading) return
    if (!reset && !this.data.hasMore) return
    if (reset) this.setData({ page: 1, hasMore: true })
    this.setData({ loading: true })

    const params = { page: this.data.page, size: this.data.size }
    if (this.data.channelId) params.channelId = this.data.channelId

    http.get('/posts', params, { silent: true }).then(function (res) {
      const records = (res.records || []).map(function (p) {
        p.timeText = timeAgo(p.createdAt)
        p.avatarText = (p.authorName || '校').substring(0, 1)
        p.excerpt = (p.content || '').replace(/\s+/g, ' ')
        p.imageList = resolveList(p.imageList)
        return p
      })
      self.setData({
        list: reset ? records : self.data.list.concat(records),
        page: self.data.page + 1,
        hasMore: res.hasMore,
        loading: false,
        loadError: false
      })
      mp.stopPullDownRefresh()
    }).catch(function () {
      self.setData({ loading: false, loadError: true })
      mp.stopPullDownRefresh()
    })
  },

  retry: function () {
    this.load(true)
  },

  switchChannel: function (e) {
    const id = Number(e.currentTarget.dataset.id) || 0
    if (id === this.data.channelId) return
    this.setData({ channelId: id })
    this.load(true)
  },

  goDetail: function (e) {
    mp.navigateTo({ url: '/pages/post/detail?id=' + e.currentTarget.dataset.id })
  },

  goPublish: function () {
    const self = this
    auth.ensureVerified().then(function () {
      const preset = self.data.channelId || (self.data.channels.length ? self.data.channels[0].id : '')
      mp.navigateTo({ url: '/pages/post/publish?channelId=' + preset })
    }).catch(function () {})
  }
})
