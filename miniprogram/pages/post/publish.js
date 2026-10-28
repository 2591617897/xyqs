const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')
const { resolveList } = require('../../utils/img')
const vis = require('../../utils/visibility')

Page({
  data: {
    id: null,
    isEdit: false,
    channels: [],
    channelId: 0,
    title: '',
    content: '',
    images: [],
    // 可见范围：默认仅本校（school），切到 city 后同城所有高校都能看到
    visibility: 'school',
    visibilityOptions: [],
    schoolName: '',
    schoolCity: '',
    agreed: false,
    submitting: false,
    loading: true
  },

  onLoad: function (options) {
    const self = this
    const postId = options && options.id
    const presetChannel = (options && options.channelId) || ''

    if (postId) {
      this.setData({ id: postId, isEdit: true })
      mp.setNavigationBarTitle({ title: '编辑帖子' })
    } else {
      mp.setNavigationBarTitle({ title: '发帖' })
      // 认证完成再刷一次：认证可能刚把学校补上，选项文案要用真实学校名
      auth.ensureVerified().then(function () {
        self.applyVisibilityOptions()
      }).catch(function () {})
    }
    this.applyVisibilityOptions()

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
            images: resolveList(post.imageList).slice(0, 9),
            // 老数据没有这个字段，normalize 兜底成 school，与服务端默认值一致
            visibility: vis.normalize(post.visibility)
          })
        })
      }
      self.setData({
        channels: channels,
        loading: false,
        channelId: presetChannel || (channels.length ? channels[0].id : '')
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
    // 雪花 ID 保持字符串，转 Number 会丢精度
    this.setData({ channelId: e.currentTarget.dataset.id })
  },

  /**
   * 可见范围选项文案依赖学校名/城市，登录后才有具体内容。
   * 没绑学校时退化成「本校 / 同市」通用文案，不出现空槽。
   */
  applyVisibilityOptions: function () {
    const u = getApp().globalData.user
    this.setData({
      schoolName: (u && u.schoolName) || '',
      schoolCity: (u && u.schoolCity) || '',
      visibilityOptions: vis.options(u && u.schoolName, u && u.schoolCity)
    })
  },

  /** 切换可见范围：school 仅本校 / city 同市可见 */
  chooseVisibility: function (e) {
    this.setData({ visibility: vis.normalize(e.currentTarget.dataset.value) })
  },

  /** 规范与协议确认：UGC 发布前必须由用户主动勾选 */
  toggleAgree: function () {
    this.setData({ agreed: !this.data.agreed })
  },

  goRule: function () {
    mp.navigateTo({ url: '/pages/legal/rule' })
  },

  goAgreement: function () {
    mp.navigateTo({ url: '/pages/legal/agreement' })
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
    if (!auth.guard()) return
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
    if (!d.agreed) {
      toast('请先勾选同意社区规范')
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
        images: urls,
        visibility: vis.normalize(d.visibility)
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
