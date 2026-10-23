const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')
const { resolveList } = require('../../utils/img')

Page({
  data: {
    id: null,
    isEdit: false,
    title: '',
    description: '',
    price: '',
    originalPrice: '',
    categoryId: 6,
    categories: [],
    images: [],
    tradeType: 1,
    location: '',
    submitting: false,
    loadError: false
  },

  onLoad: function (options) {
    const id = options && options.id
    if (id) {
      this.setData({ id: id, isEdit: true })
      mp.setNavigationBarTitle({ title: '编辑闲置' })
      this.loadGoods(id)
    } else {
      mp.setNavigationBarTitle({ title: '发布闲置' })
      auth.ensureVerified().catch(function () {})
    }
    this.loadCategories()
  },

  loadCategories: function () {
    const self = this
    http.get('/goods/categories', null, { silent: true }).then(function (list) {
      self.setData({ categories: list || [] })
    }).catch(function () {
      // 分类加载失败不阻塞发布，用后端默认分类
    })
  },

  loadGoods: function (id) {
    const self = this
    http.get('/goods/' + id, null, { silent: true }).then(function (g) {
      self.setData({
        title: g.title || '',
        description: g.description || '',
        price: g.price === null || g.price === undefined ? '' : String(g.price),
        originalPrice: g.originalPrice === null || g.originalPrice === undefined ? '' : String(g.originalPrice),
        categoryId: g.categoryId || 6,
        images: resolveList(g.imageList).slice(0, 9),
        tradeType: g.tradeType || 1,
        location: g.location || ''
      })
    }).catch(function () {
      self.setData({ loadError: true })
      toast('商品信息加载失败')
    })
  },

  onInput: function (e) {
    const patch = {}
    patch[e.currentTarget.dataset.field] = e.detail.value
    this.setData(patch)
  },

  chooseCategory: function (e) {
    this.setData({ categoryId: Number(e.currentTarget.dataset.id) })
  },

  chooseTrade: function (e) {
    this.setData({ tradeType: Number(e.currentTarget.dataset.type) })
  },

  chooseImage: function () {
    const self = this
    const rest = 9 - this.data.images.length
    if (rest <= 0) {
      toast('最多上传 9 张图片')
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

  submit: function () {
    const self = this
    const d = this.data
    if (!d.title.trim()) {
      toast('给闲置起个标题吧')
      return
    }
    const price = Number(d.price)
    if (!price || price <= 0) {
      toast('填一个靠谱的价格')
      return
    }
    if (d.originalPrice && Number(d.originalPrice) <= 0) {
      toast('原价格式不正确')
      return
    }
    if (this.data.submitting) return

    this.setData({ submitting: true })
    if (d.images.length) {
      mp.showLoading({ title: '上传图片中', mask: true })
    }

    upload.uploadImages(d.images)
      .then(function (urls) {
        if (d.images.length) mp.hideLoading()
        const payload = {
          title: d.title.trim(),
          description: d.description,
          price: price,
          originalPrice: d.originalPrice ? Number(d.originalPrice) : null,
          categoryId: d.categoryId,
          images: urls,
          tradeType: d.tradeType,
          location: d.location
        }
        return self.data.isEdit
          ? http.put('/goods/' + self.data.id, payload, { loading: '保存中' })
          : http.post('/goods', payload, { loading: '发布中' })
      })
      .then(function (res) {
        self.setData({ submitting: false })
        mp.setStorageSync('needRefreshGoods', 1)
        toast(self.data.isEdit ? '已保存' : '发布成功')
        setTimeout(function () {
          mp.navigateBack()
        }, 700)
        return res
      })
      .catch(function () {
        self.setData({ submitting: false })
        if (d.images.length) mp.hideLoading()
      })
  }
})
