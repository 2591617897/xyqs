const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')
const { resolveList } = require('../../utils/img')

/** '2023年9月' -> '2023-09'，供 picker 回显 */
function toMonth(text) {
  const m = /^(\d{4})年(\d{1,2})月$/.exec(text || '')
  return m ? m[1] + '-' + (m[2].length < 2 ? '0' + m[2] : m[2]) : ''
}

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
    // 规格与成色
    conditionLevel: 2,
    conditionOptions: [
      { id: 1, name: '全新' },
      { id: 2, name: '几乎全新' },
      { id: 3, name: '轻微使用痕迹' },
      { id: 4, name: '明显使用痕迹' }
    ],
    purchaseTime: '',
    purchaseMonth: '',
    usageFrequency: '',
    usageOptions: ['每天使用', '经常使用', '偶尔使用', '几乎没用过'],
    source: '',
    sourceOptions: ['自购自用', '朋友赠送', '活动奖品', '实验室配发', '其他'],
    warranty: '',
    warrantyOptions: ['官方保修中', '官方保修已过', '不确定'],
    supportTrial: 1,
    agreed: false,
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
        location: g.location || '',
        conditionLevel: g.conditionLevel || 2,
        purchaseTime: g.purchaseTime || '',
        purchaseMonth: toMonth(g.purchaseTime),
        usageFrequency: g.usageFrequency || '',
        source: g.source || '',
        warranty: g.warranty || '',
        supportTrial: g.supportTrial === 0 ? 0 : 1
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

  /* ---------- 规格与成色 ---------- */

  chooseCondition: function (e) {
    this.setData({ conditionLevel: Number(e.currentTarget.dataset.id) })
  },

  onPurchaseChange: function (e) {
    const ym = e.detail.value
    if (!ym) return
    const parts = ym.split('-')
    this.setData({
      purchaseTime: parts[0] + '年' + Number(parts[1]) + '月',
      purchaseMonth: ym
    })
  },

  clearPurchase: function () {
    this.setData({ purchaseTime: '', purchaseMonth: '' })
  },

  /** 单选 chips：再点一次取消选择 */
  chooseChip: function (e) {
    const field = e.currentTarget.dataset.field
    const value = e.currentTarget.dataset.value
    const patch = {}
    patch[field] = this.data[field] === value ? '' : value
    this.setData(patch)
  },

  toggleTrial: function (e) {
    this.setData({ supportTrial: e.detail.value ? 1 : 0 })
  },

  /** 发布承诺勾选：不做线上交易的确认 */
  toggleAgree: function () {
    this.setData({ agreed: !this.data.agreed })
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
    if (!d.images.length) {
      toast('至少上传一张图片吧')
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
    if (d.originalPrice && Number(d.originalPrice) < price) {
      toast('原价比售价还低啦')
      return
    }
    if (!d.agreed) {
      toast('请先勾选发布承诺（不支持线上交易）')
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
          conditionLevel: d.conditionLevel,
          purchaseTime: d.purchaseTime || null,
          usageFrequency: d.usageFrequency || null,
          source: d.source || null,
          warranty: d.warranty || null,
          supportTrial: d.supportTrial,
          images: urls,
          // 只做校内当面自提：邮寄通道已关闭，固定 tradeType=1，
          // 避免历史编辑数据把 2（邮寄）又带回去
          tradeType: 1,
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
