/**
 * 需求广场 · 列表
 * 分包：subpackages/demand —— 从「功能区」和「我的」两个入口进来，
 * 两个入口页都在 app.json 里配了 preloadRule，跳转不白屏。
 *
 * 与闲置列表的区别：不做搜索历史/热词面板（需求数据量小，面板价值低），
 * 只保留「输入即搜 + 一键清空」；分类从后端拿，前端不抄一份。
 */
const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const demandUtil = require('../../utils/demand')
const kwUtil = require('../../utils/kw')
const auth = require('../../utils/auth')

Page({
  data: {
    types: demandUtil.TYPES,
    type: 1,
    categories: [],
    categoryId: 0,
    /** 输入框里的值 */
    keyword: '',
    /** 已生效、真正进请求的关键词 */
    activeKeyword: '',
    list: [],
    page: 1,
    size: 10,
    total: 0,
    hasMore: true,
    loading: false,
    loadError: false
  },

  onLoad: function (options) {
    const t = Number(options && options.type) || 1
    this.setData({ type: t === 2 ? 2 : 1 })
    this.loadCategories()
    this.load(true)
  },

  onShow: function () {
    // 从发布页 / 详情页返回时刷新一次：新发的需求要立刻出现在自己眼前
    if (mp.getStorageSync('needRefreshDemand')) {
      mp.removeStorageSync('needRefreshDemand')
      this.load(true)
    }
  },

  onUnload: function () {
    clearTimeout(this._searchTimer)
  },

  onPullDownRefresh: function () {
    this.load(true)
  },

  onReachBottom: function () {
    this.load(false)
  },

  /** 分类取自后端（DemandService.DEMAND_CATEGORIES 是唯一数据源） */
  loadCategories: function () {
    const self = this
    http.get('/demands/categories', { type: this.data.type }, { silent: true })
      .then(function (list) {
        self.setData({ categories: list || [], categoryId: 0 })
      })
      .catch(function () {})
  },

  load: function (reset) {
    const self = this
    if (!reset && (this.data.loading || !this.data.hasMore)) return
    if (reset) this.setData({ page: 1, hasMore: true })

    const reqId = (this._reqId = (this._reqId || 0) + 1)
    this.setData({ loading: true, loadError: false })

    const params = {
      page: this.data.page,
      size: this.data.size,
      type: this.data.type
    }
    if (this.data.activeKeyword) params.keyword = this.data.activeKeyword
    if (this.data.categoryId) params.categoryId = this.data.categoryId

    http.get('/demands', params, { silent: true }).then(function (res) {
      // 请求序号对不上 = 已被更新的一次搜索顶掉，丢弃旧结果
      if (self._reqId !== reqId) return
      const kw = self.data.activeKeyword
      const records = (res.records || []).map(function (x) {
        const it = demandUtil.decorate(x)
        // 多词高亮：wxml 里没法做字符串替换，预先按每个关键词切成多段
        it.titleParts = kwUtil.splitHighlight(it.title || '', kw)
        return it
      })
      self.setData({
        list: reset ? records : self.data.list.concat(records),
        total: res.total,
        page: self.data.page + 1,
        hasMore: res.hasMore,
        loading: false,
        loadError: false
      })
      mp.stopPullDownRefresh()
    }).catch(function () {
      if (self._reqId !== reqId) return
      self.setData({ loading: false, loadError: true })
      mp.stopPullDownRefresh()
    })
  },

  retry: function () {
    this.load(true)
  },

  /* ==================== 类型 / 分类 ==================== */

  switchType: function (e) {
    const t = Number(e.currentTarget.dataset.type) || 1
    if (t === this.data.type) return
    // 换大类必须清掉二级分类：两套分类 id 各自从 1 开始，不清会串类
    this.setData({ type: t, categoryId: 0, categories: [] })
    this.loadCategories()
    this.load(true)
  },

  switchCategory: function (e) {
    const id = Number(e.currentTarget.dataset.id) || 0
    if (id === this.data.categoryId) return
    this.setData({ categoryId: id })
    this.load(true)
  },

  /* ==================== 搜索 ==================== */

  onSearchInput: function (e) {
    const v = e.detail.value || ''
    this.setData({ keyword: v })
    const self = this
    clearTimeout(this._searchTimer)
    this._searchTimer = setTimeout(function () {
      const kw = String(v || '').trim()
      if (kw === self.data.activeKeyword) return
      self.applySearch(kw)
    }, 400)
  },

  applySearch: function (kw) {
    if (kw === this.data.activeKeyword && this.data.list.length) return
    this.setData({ activeKeyword: kw })
    this.load(true)
  },

  clearKeyword: function () {
    clearTimeout(this._searchTimer)
    this.setData({ keyword: '' })
    this.applySearch('')
  },

  /* ==================== 跳转 ==================== */

  goDetail: function (e) {
    mp.navigateTo({ url: '/subpackages/demand/detail?id=' + e.currentTarget.dataset.id })
  },

  goPublish: function () {
    const self = this
    auth.ensureVerified().then(function () {
      mp.navigateTo({ url: '/subpackages/demand/publish?type=' + self.data.type })
    }).catch(function () {})
  },

  goMine: function () {
    const self = this
    auth.ensureLogin().then(function () {
      mp.navigateTo({ url: '/subpackages/demand/mine' })
    }).catch(function () {})
  }
})
