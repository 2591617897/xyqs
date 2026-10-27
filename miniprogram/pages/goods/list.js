const http = require('../../utils/request')
const { timeAgo, priceText, fmtCount, mp, categoryEmoji, conditionText, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const { resolveList } = require('../../utils/img')
// 多词搜索：拆词 / 匹配 / 高亮统一走 utils/kw，规则与后端 SearchKit 一致
const kwUtil = require('../../utils/kw')

/** 搜索历史存本地：上限 10 条，最近的在最前，重复词提到最前 */
const HISTORY_KEY = 'goodsSearchHistory'
const HISTORY_MAX = 10
/**
 * 热门搜索兜底词。
 *
 * 真正的热词来自「近 7 天真实搜索」聚合（GET /goods/hot-words，后端 HotWordsService）。
 * 这批常量只在两种情况下露出：接口请求还没回来（首屏几十毫秒），或者接口挂了。
 * 所以它要跟后端的 FALLBACK 保持一致——两边都列同一批校园高频词，用户看不出切换。
 */
const FALLBACK_HOTS = ['自行车', '考研书', '显示器', '电动车', '台灯', '耳机', '球拍', '被子']
/** 热词缓存：10 分钟内重复进页面不再打接口（热词一天也变不了几次） */
const HOTS_TTL = 10 * 60 * 1000

Page({
  data: {
    /** 输入框里的实时值（可能还没搜） */
    keyword: '',
    /** 已经生效、正在用于查询的关键词（列表头部「共 N 件」对应它） */
    activeKeyword: '',
    /** 生效关键词拆出来的词（多词搜索时用来提示「N 个关键词 / 要同时命中」） */
    kwWords: [],
    /** 搜索历史 / 热词 / 面板 */
    history: [],
    hots: FALLBACK_HOTS,
    showPanel: false,

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
    this.loadHots()
    this.setData({ history: mp.getStorageSync(HISTORY_KEY) || [] })
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

  onUnload: function () {
    // 定时器必须清：页面反复进出会累积 debounce，导致已经离开的页面还在发请求
    clearTimeout(this._searchTimer)
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

    if (this.data.mineMode || this.data.favMode) {
      if (this.data.loading) return
      this.setData({ loading: true })
      auth.ensureLogin().then(function () {
        return http.get(self.data.favMode ? '/goods/favorites' : '/goods/mine')
      }).then(function (list) {
        self.setData({
          list: (list || []).map(function (x) { return decorate(x, '') }),
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

    // 上拉加载：正在加载中或没有更多就跳过；
    // 但 reset（切分类 / 换排序 / 改关键词）必须能打断正在飞的请求，
    // 否则「输入后立刻点搜索」会被 loading 吞掉，列表停在旧结果。
    if (!reset && (this.data.loading || !this.data.hasMore)) return
    if (reset) this.setData({ page: 1, hasMore: true })

    const reqId = (this._reqId = (this._reqId || 0) + 1)
    this.setData({ loading: true })

    const params = {
      page: this.data.page,
      size: this.data.size,
      sort: this.data.sort
    }
    if (this.data.activeKeyword) params.keyword = this.data.activeKeyword
    if (this.data.categoryId) params.categoryId = this.data.categoryId

    http.get('/goods', params, { silent: true }).then(function (res) {
      // 请求序号对不上 = 已被更新的一次搜索顶掉，直接丢弃（避免旧结果覆盖新结果）
      if (self._reqId !== reqId) return
      const kw = self.data.activeKeyword
      const records = (res.records || []).map(function (x) { return decorate(x, kw) })
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

  /** 封面图加载失败时回退到占位图 */
  onImgError: function (e) {
    const index = e.currentTarget.dataset.index
    if (index === undefined || index === null) return
    const patch = {}
    patch['list[' + index + '].imgFailed'] = true
    this.setData(patch)
  },

  /* ==================== 搜索 ==================== */

  /** 输入即搜（400ms 防抖）：中文输入法会连续触发 bindinput，靠防抖收敛成一次查询 */
  onSearchInput: function (e) {
    const v = e.detail.value || ''
    this.setData({ keyword: v, showPanel: !v })
    this.scheduleSearch(v)
  },

  scheduleSearch: function (v) {
    const self = this
    clearTimeout(this._searchTimer)
    this._searchTimer = setTimeout(function () {
      const kw = String(v || '').trim()
      // 和已生效的关键词一样就不重复打接口
      if (kw === self.data.activeKeyword) return
      self.applySearch(kw, { record: false, syncInput: false })
    }, 400)
  },

  /** 回车 / 点「搜索」：立即搜 + 记历史 + 收键盘 */
  onSearch: function () {
    clearTimeout(this._searchTimer)
    const kw = String(this.data.keyword || '').trim()
    this.hideKeyboard()
    if (!kw) {
      // 空关键词 = 退出搜索态，回到全部闲置
      this.applySearch('', { record: false })
      return
    }
    this.applySearch(kw, { record: true })
  },

  /**
   * 让关键词生效。
   * 注意 activeKeyword 才是真正进请求的参数，keyword 只是输入框的值 ——
   * 分开之后「边打边搜」才不会因为每次按键都重置列表而闪烁。
   */
  applySearch: function (kw, opts) {
    opts = opts || {}
    const changed = kw !== this.data.activeKeyword
    const patch = { activeKeyword: kw, showPanel: false, kwWords: kwUtil.words(kw) }
    // 边打边搜时不要回写输入框：否则用户打「苹果 」会被 trim 成「苹果」，光标会跳
    if (opts.syncInput !== false) patch.keyword = kw
    this.setData(patch)
    if (opts.record) this.pushHistory(kw)
    if (!changed && this.data.list.length) return
    this.load(true)
  },

  /** 搜索历史：去重 + 提到最前 + 上限 10 条 */
  pushHistory: function (kw) {
    if (!kw) return
    const next = [kw]
      .concat((this.data.history || []).filter(function (w) { return w !== kw }))
      .slice(0, HISTORY_MAX)
    this.setData({ history: next })
    mp.setStorageSync(HISTORY_KEY, next)
  },

  /** 点历史 / 热词：直接搜 */
  onPickWord: function (e) {
    const w = e.currentTarget.dataset.word || ''
    clearTimeout(this._searchTimer)
    this.hideKeyboard()
    this.applySearch(w, { record: true })
  },

  /** 删单条历史（catchtap，别让冒泡触发 onPickWord 重搜） */
  onDelHistory: function (e) {
    const w = e.currentTarget.dataset.word
    const next = (this.data.history || []).filter(function (x) { return x !== w })
    this.setData({ history: next })
    mp.setStorageSync(HISTORY_KEY, next)
  },

  onClearHistory: function () {
    this.setData({ history: [] })
    mp.removeStorageSync(HISTORY_KEY)
    toast('已清空搜索历史')
  },

  clearKeyword: function () {
    clearTimeout(this._searchTimer)
    this.applySearch('', { record: false })
  },

  /**
   * 拉真实热词（近 7 天搜索聚合）。
   *
   * 失败一律静默：面板里已经有兜底词，这次拉不到用户完全感知不到，
   * 弹一个「热词加载失败」只会让人莫名其妙。把 _hotsAt 归零，下次进面板再试。
   */
  loadHots: function () {
    const self = this
    const now = Date.now()
    // TTL 内不重复请求：本页是 navigateTo 进来的，来回进出很频繁，且每次聚焦都会调到这里
    if (this._hotsAt && now - this._hotsAt < HOTS_TTL) return
    this._hotsAt = now

    http.get('/goods/hot-words', { days: 7, limit: 8 }, { silent: true })
      .then(function (res) {
        const words = ((res && res.data) || []).filter(function (w) {
          return typeof w === 'string' && w
        })
        // 接口永远会补齐默认词，返回空说明是真出问题了，保持现有列表不动
        if (!words.length) return
        self.setData({ hots: words })
      })
      .catch(function () {
        self._hotsAt = 0
      })
  },

  /** 输入框获得焦点且还没输入时，露出历史/热词面板 */
  onSearchFocus: function () {
    if (!this.data.keyword) this.setData({ showPanel: true })
    // loadHots 自己按 TTL 判断，这里无脑调即可：过期才真正发请求
    this.loadHots()
  },

  closePanel: function () {
    this.setData({ showPanel: false })
  },

  hideKeyboard: function () {
    if (typeof mp.hideKeyboard === 'function') mp.hideKeyboard()
  },

  /* ==================== 筛选 ==================== */

  switchCategory: function (e) {
    const id = Number(e.currentTarget.dataset.id) || 0
    if (id === this.data.categoryId) return
    this.setData({ categoryId: id, showPanel: false })
    this.load(true)
  },

  switchSort: function (e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.sort) return
    this.setData({ sort: key, showPanel: false })
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

/**
 * 列表项加工。
 * kw 非空时额外算 titleParts，把标题按**每个关键词**切成多段给 wxml 高亮渲染
 * （wxml 里没法做字符串替换，只能预先切好）。
 */
function decorate(item, kw) {
  item.timeText = timeAgo(item.createdAt)
  item.priceText = priceText(item.price)
  item.emoji = categoryEmoji(item.categoryId)
  item.imageList = resolveList(item.imageList)
  item.titleParts = kwUtil.splitHighlight(item.title || '', kw)
  // 描述摘要：标题短的时候用它把卡片撑起来，长描述只留一行
  item.descText = (item.description || '').replace(/\s+/g, ' ').trim()
  item.conditionText = conditionText(item.conditionLevel)
  item.viewText = fmtCount(item.viewCount || 0)
  item.likeText = fmtCount(item.likeCount || 0)
  return item
}
