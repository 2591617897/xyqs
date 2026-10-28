/**
 * 需求广场 · 发布 / 编辑
 *
 * 与闲置发布页的差异：
 *  - 图片不是必填（需求是「描述想要什么」，参考图可有可无）
 *  - 预算可空 = 面议：求助类需求常常没有报酬，强制填金额会把这类需求挡在门外
 *  - 二级分类跟着大类走，换大类必须清掉已选分类（两套 id 各自从 1 开始）
 */
const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const upload = require('../../utils/upload')
const vis = require('../../utils/visibility')
const demandUtil = require('../../utils/demand')
const auth = require('../../utils/auth')

/** 期望时间快捷项：点一下填进输入框，减少打字 */
const QUICK_TIMES = ['不限', '今天', '明天', '本周内', '本周末']

Page({
  data: {
    id: '',
    isEdit: false,
    types: demandUtil.TYPES,
    type: 1,
    categories: [],
    categoryId: 0,

    title: '',
    description: '',
    /** 面议开关：打开时提交 budget=null */
    negotiable: false,
    budget: '',
    expectTime: '',
    location: '',
    images: [],

    visibility: 'school',
    visibilityOptions: [],

    quickTimes: QUICK_TIMES,
    submitting: false,
    loading: false
  },

  onLoad: function (options) {
    const t = Number(options && options.type) || 1
    this.setData({ type: t === 2 ? 2 : 1 })

    // 发布/编辑都要学籍认证（后端 requireVerifiedUserId），不先拦一次会白填一屏表单
    auth.ensureVerified().catch(function () {
      setTimeout(function () { mp.navigateBack() }, 300)
    })

    const cached = mp.getStorageSync('user') || {}
    this.setData({ visibilityOptions: vis.options(cached.schoolName, cached.schoolCity) })

    const id = options && options.id
    if (id) {
      this.setData({ id: id, isEdit: true, loading: true })
      this.loadDetail(id)
    } else {
      this.loadCategories()
    }
  },

  loadCategories: function () {
    const self = this
    http.get('/demands/categories', { type: this.data.type }, { silent: true })
      .then(function (list) { self.setData({ categories: list || [] }) })
      .catch(function () {})
  },

  loadDetail: function (id) {
    const self = this
    http.get('/demands/' + id, null, { silent: true }).then(function (d) {
      if (!d) return
      self.setData({
        type: d.type || 1,
        categoryId: d.categoryId || 0,
        title: d.title || '',
        description: d.description || '',
        negotiable: d.budget === null || d.budget === undefined,
        budget: d.budget === null || d.budget === undefined ? '' : String(d.budget),
        expectTime: d.expectTime || '',
        location: d.location || '',
        images: d.imageList || [],
        visibility: vis.normalize(d.visibility),
        loading: false
      })
      self.loadCategories()
    }).catch(function () {
      self.setData({ loading: false })
      toast('需求加载失败')
    })
  },

  /* ==================== 表单 ==================== */

  switchType: function (e) {
    const t = Number(e.currentTarget.dataset.type) || 1
    if (t === this.data.type) return
    // 换大类必须清分类：两套分类 id 各自从 1 开始，不清会串类
    this.setData({ type: t, categoryId: 0, categories: [] })
    this.loadCategories()
  },

  switchCategory: function (e) {
    this.setData({ categoryId: Number(e.currentTarget.dataset.id) || 0 })
  },

  onTitle: function (e) { this.setData({ title: e.detail.value || '' }) },
  onDesc: function (e) { this.setData({ description: e.detail.value || '' }) },
  onBudget: function (e) { this.setData({ budget: e.detail.value || '' }) },
  onExpect: function (e) { this.setData({ expectTime: e.detail.value || '' }) },
  onLocation: function (e) { this.setData({ location: e.detail.value || '' }) },

  toggleNegotiable: function () {
    this.setData({ negotiable: !this.data.negotiable })
  },

  pickQuickTime: function (e) {
    this.setData({ expectTime: e.currentTarget.dataset.value || '' })
  },

  switchVisibility: function (e) {
    this.setData({ visibility: vis.normalize(e.currentTarget.dataset.value) })
  },

  chooseImage: function () {
    const self = this
    const rest = 3 - this.data.images.length
    if (rest <= 0) {
      toast('最多上传 3 张图片')
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
    const index = e.currentTarget.dataset.index
    const images = this.data.images.slice()
    images.splice(index, 1)
    this.setData({ images: images })
  },

  previewImage: function (e) {
    mp.previewImage({ current: e.currentTarget.dataset.url, urls: this.data.images })
  },

  /* ==================== 提交 ==================== */

  submit: function () {
    const self = this
    if (!auth.guard()) return
    const d = this.data
    if (!String(d.title).trim()) {
      toast('说说你想要什么')
      return
    }
    if (!String(d.description).trim()) {
      toast('补充一下具体要求吧')
      return
    }
    if (!d.negotiable && String(d.budget).trim() === '') {
      toast('填写预算，或勾选「面议」')
      return
    }
    if (d.submitting) return
    this.setData({ submitting: true })

    const payload = {
      type: d.type,
      title: String(d.title).trim(),
      description: String(d.description).trim(),
      categoryId: d.categoryId,
      budget: d.negotiable ? null : Number(d.budget),
      expectTime: String(d.expectTime).trim(),
      location: String(d.location).trim(),
      images: [],
      visibility: vis.normalize(d.visibility)
    }

    // 本地图（wxfile://）必须先换成服务端 URL 再提交，否则库里存的是临时路径，
    // 别人打开详情永远加载不出来 —— 学籍认证那次就是这么踩的
    upload.uploadImages(d.images).then(function (urls) {
      payload.images = urls || []
      if (self.data.isEdit) {
        return http.put('/demands/' + self.data.id, payload).then(function () { return null })
      }
      return http.post('/demands', payload)
    }).then(function () {
      self.setData({ submitting: false })
      toast(self.data.isEdit ? '已保存' : '发布成功')
      mp.setStorageSync('needRefreshDemand', 1)
      setTimeout(function () { mp.navigateBack() }, 600)
    }).catch(function () {
      self.setData({ submitting: false })
    })
  }
})
