const http = require('../../utils/request')
const { timeAgo, mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')
const { resolveList } = require('../../utils/img')

Page({
  data: {
    posts: [],
    page: 1,
    size: 10,
    hasMore: true,
    total: 0,
    sort: 'new',
    loading: false,
    loadError: false,
    showCompose: false,
    content: '',
    images: [],
    submitting: false
  },

  onLoad: function () {
    mp.setNavigationBarTitle({ title: '匿名表白墙' })
    this.load(true)
  },

  onShow: function () {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 3 })
    }
    // 从详情页返回时同步点赞数 / 评论数 / 编辑结果
    if (mp.getStorageSync('needRefreshWall')) {
      mp.removeStorageSync('needRefreshWall')
      this.load(true)
      return
    }
    if (this.data.posts.length === 0) this.load(true)
  },

  onPullDownRefresh: function () {
    this.load(true)
  },

  onReachBottom: function () {
    this.load(false)
  },

  load: function (reset) {
    const self = this
    if (this.data.loading) return
    if (!reset && !this.data.hasMore) return
    if (reset) this.setData({ page: 1, hasMore: true })
    this.setData({ loading: true })
    http.get('/wall/posts', {
      page: this.data.page,
      size: this.data.size,
      sort: this.data.sort
    }, { silent: true })
      .then(function (res) {
        const records = (res.records || []).map(decorate)
        self.setData({
          posts: reset ? records : self.data.posts.concat(records),
          page: self.data.page + 1,
          total: res.total || (reset ? records.length : self.data.total + records.length),
          hasMore: res.hasMore,
          loading: false,
          loadError: false
        })
        mp.stopPullDownRefresh()
      })
      .catch(function () {
        self.setData({ loading: false, loadError: true })
        mp.stopPullDownRefresh()
      })
  },

  switchSort: function (e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.sort) return
    this.setData({ sort: key })
    this.load(true)
  },

  /* ---------- 浏览 ---------- */
  retry: function () {
    this.load(true)
  },

  goDetail: function (e) {
    mp.navigateTo({ url: '/pages/wall/detail?id=' + e.currentTarget.dataset.id })
  },

  preview: function (e) {
    const index = e.currentTarget.dataset.index
    const post = this.data.posts[index]
    if (!post || !post.imageList.length) return
    mp.previewImage({
      current: e.currentTarget.dataset.url,
      urls: post.imageList
    })
  },

  /* ---------- 点赞（不冒泡到卡片） ---------- */
  toggleLike: function (e) {
    const self = this
    const index = e.currentTarget.dataset.index
    const post = this.data.posts[index]
    if (!post) return
    auth.ensureLogin().then(function () {
      return http.post('/wall/posts/' + post.id + '/like')
    }).then(function (liked) {
      const key = 'posts[' + index + ']'
      const patch = {}
      patch[key + '.liked'] = liked
      patch[key + '.likeCount'] = Math.max(0, (post.likeCount || 0) + (liked ? 1 : -1))
      self.setData(patch)
    }).catch(function () {})
  },

  /* ---------- 发布 ---------- */
  openCompose: function () {
    const self = this
    auth.ensureVerified().then(function () {
      self.setData({ showCompose: true })
    }).catch(function () {})
  },

  closeCompose: function () {
    this.setData({ showCompose: false })
  },

  onContent: function (e) {
    this.setData({ content: e.detail.value })
  },

  chooseImage: function () {
    const self = this
    const rest = 3 - this.data.images.length
    if (rest <= 0) {
      toast('最多 3 张图片')
      return
    }
    mp.chooseImage({
      count: rest,
      sizeType: ['compressed'],
      success: function (res) {
        self.setData({ images: self.data.images.concat(res.tempFilePaths) })
      }
    })
  },

  removeImage: function (e) {
    const images = this.data.images.slice()
    images.splice(e.currentTarget.dataset.index, 1)
    this.setData({ images: images })
  },

  submitPost: function () {
    const self = this
    if (!this.data.content.trim()) {
      toast('说点什么再发吧')
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })
    if (this.data.images.length) {
      mp.showLoading({ title: '上传图片中', mask: true })
    }
    upload.uploadImages(this.data.images).then(function (urls) {
      if (self.data.images.length) mp.hideLoading()
      return http.post('/wall/posts', {
        content: self.data.content.trim(),
        images: urls
      }, { loading: '发布中' })
    }).then(function () {
      self.setData({ showCompose: false, content: '', images: [], submitting: false })
      toast('已匿名发布')
      self.load(true)
    }).catch(function () {
      self.setData({ submitting: false })
      mp.hideLoading()
    })
  },

  /* ---------- 删除（不冒泡） ---------- */
  deletePost: function (e) {
    const self = this
    const post = this.data.posts[e.currentTarget.dataset.index]
    if (!post) return
    mp.showModal({
      title: '删除这条心事？',
      content: '删除后不可恢复',
      confirmColor: '#ff4d4f',
      success: function (res) {
        if (!res.confirm) return
        http.del('/wall/posts/' + post.id).then(function () {
          toast('已删除')
          self.load(true)
        }).catch(function () {})
      }
    })
  },

  stop: function () {}
})

function decorate(item) {
  item.timeText = timeAgo(item.createdAt)
  item.avatarClass = 'anon-' + (item.anonAvatar || 1)
  item.avatarEmoji = ['🌸', '🌙', '🍀', '⭐', '🐳', '🍑', '🌿', '☁️'][(item.anonAvatar || 1) - 1]
  item.imageList = resolveList(item.imageList)
  return item
}
