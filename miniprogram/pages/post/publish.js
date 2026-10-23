const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')

Page({
  data: {
    channels: [],
    channelId: 0,
    title: '',
    content: '',
    images: [],
    submitting: false
  },

  onLoad: function (options) {
    const self = this
    mp.setNavigationBarTitle({ title: '发帖' })
    auth.ensureVerified().catch(function () {})
    http.get('/channels', null, { silent: true }).then(function (list) {
      const channels = list || []
      let channelId = Number(options.id) || 0
      if (!channelId && channels.length) channelId = channels[0].id
      self.setData({ channels: channels, channelId: channelId })
    }).catch(function () {})
  },

  onInput: function (e) {
    const patch = {}
    patch[e.currentTarget.dataset.field] = e.detail.value
    this.setData(patch)
  },

  chooseChannel: function (e) {
    this.setData({ channelId: Number(e.currentTarget.dataset.id) })
  },

  chooseImage: function () {
    const self = this
    const rest = 9 - this.data.images.length
    if (rest <= 0) return
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

  submit: function () {
    const self = this
    const d = this.data
    if (!d.channelId) {
      toast('请选择频道')
      return
    }
    if (!d.title.trim()) {
      toast('给帖子起个标题')
      return
    }
    if (!d.content.trim()) {
      toast('正文还没写呢')
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })
    if (d.images.length) {
      mp.showLoading({ title: '上传图片中', mask: true })
    }
    upload.uploadImages(d.images).then(function (urls) {
      if (d.images.length) mp.hideLoading()
      return http.post('/posts', {
        channelId: d.channelId,
        title: d.title.trim(),
        content: d.content.trim(),
        images: urls
      }, { loading: '发布中' })
    }).then(function () {
      toast('发布成功')
      mp.setStorageSync('needRefreshPosts', 1)
      setTimeout(function () {
        mp.navigateBack()
      }, 700)
    }).catch(function () {
      self.setData({ submitting: false })
      if (d.images.length) mp.hideLoading()
    })
  }
})
