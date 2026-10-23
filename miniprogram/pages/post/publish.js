const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')
const { resolveList } = require('../../utils/img')

Page({
  data: {
    id: null,
    isEdit: false,
    channels: [],
    channelId: 0,
    title: '',
    content: '',
    images: [],
    submitting: false,
    loading: true
  },

  onLoad: function (options) {
    const self = this
    const postId = options && options.id
    const presetChannel = Number(options && options.channelId) || 0

    if (postId) {
      this.setData({ id: postId, isEdit: true })
      mp.setNavigationBarTitle({ title: '编辑帖子' })
    } else {
      mp.setNavigationBarTitle({ title: '发帖' })
      auth.ensureVerified().catch(function () {})
    }

    http.get('/channels', null, { silent: true }).then(function (list) {
      const channels = list || []
      if (self.data.isEdit) {
        return http.get('/posts/' + postId, null, { silent: true }).then(function (post) {
          self.setData({
            channels: channels,
            loading: false,
            channelId: post.channelId,
            title: post.title || '',
            content: post.content || '',
            images: resolveList(post.imageList).slice(0, 9)
          })
        })
      }
      self.setData({
        channels: channels,
        loading: false,
        channelId: presetChannel || (channels.length ? channels[0].id : 0)
      })
    }).catch(function () {
      self.setData({ loading: false })
      toast(self.data.isEdit ? '帖子加载失败' : '频道加载失败')
    })
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
    if (rest <= 0) {
      toast('最多 9 张图片')
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

  previewImage: function (e) {
    mp.previewImage({ current: e.currentTarget.dataset.url, urls: this.data.images })
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
      const payload = {
        channelId: d.channelId,
        title: d.title.trim(),
        content: d.content.trim(),
        images: urls
      }
      return self.data.isEdit
        ? http.put('/posts/' + self.data.id, payload, { loading: '保存中' })
        : http.post('/posts', payload, { loading: '发布中' })
    }).then(function () {
      self.setData({ submitting: false })
      toast(self.data.isEdit ? '已保存' : '发布成功')
      mp.setStorageSync('needRefreshPosts', 1)
      setTimeout(function () {
        mp.navigateBack()
      }, 700)
    }).catch(function () {
      self.setData({ submitting: false })
      mp.hideLoading()
    })
  }
})
