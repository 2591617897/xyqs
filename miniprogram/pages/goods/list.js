const http = require('../../utils/request')
const { timeAgo, priceText, mp, categoryEmoji, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const { resolveList } = require('../../utils/img')

Page({
  data: {
    keyword: '',
    categories: [],
    categoryId: 0,
    sort: 'latest',
    sorts: [
      { key: 'latest', label: '最新' },
      { key: 'price_asc', label: '价格 ↑' },
      { key: 'price_desc', label: '价格 ↓' },
      { key: 'hot', label: '热门' }
    ],
    list: [],
    page: 1,
    size: 10,
    total: 0,
    hasMore: true,
    loading: false,
    loadError: false,
    mineMode: false,
    favMode: false
  },

  onLoad: function () {
    this.loadCategories()
  },

  onShow: function () {
    // 「闲置」已不是底栏 tab（让位给「功能区」），本页从功能区/首页 navigateTo 进来，
    // 没有 getTabBar 可设置，也不用管选中态

    const app = getApp()
    const mode = (app.globalData && app.globalData.goodsMode) || ''
    const mineMode = mode === 'mine'
    const favMode = mode === 'fav'
    if (mineMode !== this.data.mineMode || favMode !== this.data.favMode) {
      this.setData({ mineMode: mineMode, favMode: favMode, list: [] })
      mp.setNavigationBarTitle({
        title: mineMode ? '我的闲置' : favMode ? '我的收藏' : '闲置交换'
      })
      this.load(true)
      return
    }

    const pending = mp.getStorageSync('pendingCategory')
    if (pending) {
      mp.removeStorageSync('pendingCategory')
      this.setData({ categoryId: pending })
      this.load(true)
      return
    }

    if (mp.getStorageSync('needRefreshGoods')) {
      mp.removeStorageSync('needRefreshGoods')
      this.load(true)
      return
    }

    if (!this.data.list.length) this.load(true)
  },

  onPullDownRefresh: function () {
    this.load(true)
  },

  onReachBottom: function () {
    if (!this.data.mineMode && !this.data.favMode) this.load(false)
  },

  loadCategories: function () {
    const self = this
    http.get('/goods/categories', null, { silent: true }).then(function (list) {
      self.setData({ categories: list || [] })
    }).catch(function () {})
  },

  load: function (reset) {
    const self = this
    if (this.data.loading) return
    if (reset) {
      this.setData({ page: 1, hasMore: true })
    } else if (!this.data.hasMore) {
      return
    }
    this.setData({ loading: true })

    if (this.data.mineMode || this.data.favMode) {
      auth.ensureLogin().then(function () {
        return http.get(self.data.favMode ? '/goods/favorites' : '/goods/mine')
      }).then(function (list) {
        self.setData({
          list: (list || []).map(decorate),
          total: (list || []).length,
          loading: false,
          loadError: false,
          hasMore: false
        })
        mp.stopPullDownRefresh()
      }).catch(function () {
        self.setData({ loading: false, loadError: true })
        mp.stopPullDownRefresh()
      })
      return
    }

    const params = {
      page: this.data.page,
      size: this.data.size,
      sort: this.data.sort
    }
    if (this.data.keyword) params.keyword = this.data.keyword
    if (this.data.categoryId) params.categoryId = this.data.categoryId

    http.get('/goods', params, { silent: true }).then(function (res) {
      const records = (res.records || []).map(decorate)
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
      self.setData({ loading: false, loadError: true })
      mp.stopPullDownRefresh()
    })
  },

  retry: function () {
    this.load(true)
  },

  /** 封面图加载失败时回退到占位图 */
  onImgError: function (e) {
    const index = e.currentTarget.dataset.index
    if (index === undefined || index === null) return
    const patch = {}
    patch['list[' + index + '].imgFailed'] = true
    this.setData(patch)
  },

  onSearchInput: function (e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch: function () {
    this.load(true)
  },

  clearKeyword: function () {
    this.setData({ keyword: '' })
    this.load(true)
  },

  switchCategory: function (e) {
    const id = Number(e.currentTarget.dataset.id) || 0
    if (id === this.data.categoryId) return
    this.setData({ categoryId: id })
    this.load(true)
  },

  switchSort: function (e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.sort) return
    this.setData({ sort: key })
    this.load(true)
  },

  goDetail: function (e) {
    mp.navigateTo({ url: '/pages/goods/detail?id=' + e.currentTarget.dataset.id })
  },

  goPublish: function () {
    auth.ensureVerified().then(function () {
      mp.navigateTo({ url: '/pages/goods/publish' })
    }).catch(function () {})
  }
})

function decorate(item) {
  item.timeText = timeAgo(item.createdAt)
  item.priceText = priceText(item.price)
  item.emoji = categoryEmoji(item.categoryId)
  item.imageList = resolveList(item.imageList)
  return item
}
