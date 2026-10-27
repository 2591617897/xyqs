/**
 * 我的 · 我的动态（帖子 + 树洞合并时间线）
 * 数据：GET /posts?userId= / GET /wall/posts?userId=
 *       userId 存在时后端只返回该作者 status=1 的内容，并忽略 school/city 范围。
 *
 * 分页：两个源各自分页，取同一页码同时拉，合并后按时间倒序重排 + 去重。
 * （先分别 append 再整体排序，避免「帖子块」和「树洞块」各自成段）
 */
const http = require('../../../utils/request')
const auth = require('../../../utils/auth')
const { mp, timeAgo } = require('../../../utils/util')
const { resolveList } = require('../../../utils/img')

const PAGE_SIZE = 20

/** 默认导航标题（带 cat 进来时会被替换，手动切 tab 后还原） */
const DEFAULT_TITLE = '我的动态'

const FLAIR_MAP = {
  love: '💗 精选表白',
  tree: '🌳 树洞吐槽',
  find: '🔍 寻物启事',
  findp: '🎣 捞人'
}

const TABS = [
  { key: 'all', label: '全部' },
  { key: 'post', label: '帖子' },
  { key: 'wall', label: '树洞' }
]

/** URL 上允许传的分类（= 树洞 category 枚举），白名单外的直接忽略 */
const CATS = {
  love: '💗 我的表白',
  tree: '🌳 我的树洞',
  find: '🔍 我的寻物',
  findp: '🎣 我的捞人'
}

Page({
  data: {
    tabs: [],
    tab: 'all',
    cat: '',        // 分类筛选（仅树洞有 category），空 = 不筛
    emptyTitle: '还没有任何动态',
    master: [],     // 合并后的全量（帖子 + 树洞）
    shown: [],      // 当前 tab 过滤结果
    total: 0,
    page: 1,
    hasMore: false,
    loading: true,      // 首屏加载 / 下拉刷新
    loadingMore: false, // 上拉加载
    loadError: false,
    needLogin: false
  },

  /**
   * 支持从「我的」页带参进入：?tab=wall&cat=love
   * onLoad 早于 onShow，所以这里设好的 tab/cat 会被首次 load() 后的 applyTab 直接用上。
   * cat 必须配 tab=wall（只有树洞带 category），否则按不筛处理。
   */
  onLoad: function (options) {
    const q = options || {}
    const cat = Object.prototype.hasOwnProperty.call(CATS, q.cat) ? q.cat : ''
    const tab = q.tab === 'post' || q.tab === 'wall' ? q.tab : (cat ? 'wall' : 'all')
    this.setData({ tab: tab, cat: cat })
    if (cat) {
      mp.setNavigationBarTitle({ title: CATS[cat] })
    }
  },

  onShow: function () {
    this.load()
  },

  onPullDownRefresh: function () {
    this.load(true)
  },

  onReachBottom: function () {
    if (this.data.hasMore && !this.data.loadingMore && !this.data.loading) this.loadMore()
  },

  /** 拉第 page 页的两个源，返回 { list, hasMore } */
  fetchPage: function (userId, page) {
    const query = { userId: userId, page: page, size: PAGE_SIZE }
    const empty = { records: [], hasMore: false }
    // 两个 Promise 各自 catch：一个源挂了不能拖死另一个
    return Promise.all([
      http.get('/posts', query, { silent: true }).catch(function (err) {
        console.error('[me/dynamic] 帖子列表失败', err)
        return empty
      }),
      http.get('/wall/posts', query, { silent: true }).catch(function (err) {
        console.error('[me/dynamic] 树洞列表失败', err)
        return empty
      })
    ]).then(function (res) {
      const posts = (res[0] && res[0].records) || []
      const walls = (res[1] && res[1].records) || []
      const list = posts.map(function (p) {
        return decorate(Object.assign({}, p, { _type: 'post', _key: 'p-' + p.id }))
      }).concat(walls.map(function (w) {
        return decorate(Object.assign({}, w, {
          _type: 'wall',
          _key: 'w-' + w.id,
          flair: FLAIR_MAP[w.category] || '✨ 树洞'
        }))
      }))
      return {
        list: list,
        hasMore: !!(res[0] && res[0].hasMore) || !!(res[1] && res[1].hasMore)
      }
    })
  },

  load: function (refreshing) {
    const self = this
    if (!refreshing && !this.data.master.length) this.setData({ loading: true })
    this.setData({ loadError: false })

    auth.ensureLogin().then(function () {
      const user = getApp().globalData.user
      const meId = user && user.id ? String(user.id) : ''
      if (!meId) throw { code: 401, msg: 'no user' }
      return self.fetchPage(meId, 1)
    }).then(function (res) {
      self.setData({
        master: res.list,
        page: 1,
        hasMore: res.hasMore,
        loading: false,
        loadError: false,
        needLogin: false
      })
      self.applyTab(self.data.tab)
      mp.stopPullDownRefresh()
    }).catch(function (err) {
      const noAuth = !!(err && (err.code === 401 || err.code === 403)) || !http.getToken()
      self.setData({ loading: false, loadError: !noAuth, needLogin: noAuth })
      mp.stopPullDownRefresh()
    })
  },

  loadMore: function () {
    const self = this
    const user = getApp().globalData.user
    const meId = user && user.id ? String(user.id) : ''
    if (!meId) return
    const next = this.data.page + 1
    this.setData({ loadingMore: true })

    this.fetchPage(meId, next).then(function (res) {
      // 按 _key 去重（翻页期间有新发布时，第二页可能出现第一页已经有的条目）
      const seen = {}
      self.data.master.forEach(function (it) { seen[it._key] = true })
      const merged = self.data.master.concat(res.list.filter(function (it) { return !seen[it._key] }))
      // 合并后再整体按时间倒序，保证交错顺序正确
      merged.sort(function (a, b) { return b._ts - a._ts })
      self.setData({ master: merged, page: next, hasMore: res.hasMore, loadingMore: false })
      self.applyTab(self.data.tab)
    }).catch(function () {
      self.setData({ loadingMore: false })
    })
  },

  switchTab: function (e) {
    const key = e.currentTarget.dataset.key
    if (!key) return
    // ⚠️ 不能只判 key === tab 就 return：带 cat=love 进来时 tab 已经是 wall，
    // 用户点「树洞」（当前选中项）会被这条挡掉，分类筛选永远清不掉，看着像页面坏了。
    // 所以「点当前 tab」在有 cat 时的语义是「放弃筛选」，必须放行。
    if (key === this.data.tab && !this.data.cat) return
    // 手动切 tab 视为放弃分类筛选：带 cat=love 进来后点「树洞」，应该看到全部树洞而不是只有表白
    this.setData({ cat: '' })
    mp.setNavigationBarTitle({ title: DEFAULT_TITLE })
    this.applyTab(key)
  },

  applyTab: function (key) {
    const master = this.data.master
    const cat = this.data.cat
    const countOf = function (k) {
      if (k === 'all') return master.length
      return master.filter(function (it) { return it._type === k }).length
    }
    const shown = master.filter(function (it) {
      if (key !== 'all' && it._type !== key) return false
      // cat 只对树洞生效（帖子没有 category 字段）
      if (cat && !(it._type === 'wall' && it.category === cat)) return false
      return true
    })
    this.setData({
      tab: key,
      tabs: TABS.map(function (t) {
        return { key: t.key, label: t.label, count: countOf(t.key) }
      }),
      shown: shown,
      emptyTitle: emptyTitleOf(key, cat)
    })
  },

  goDetail: function (e) {
    const type = e.currentTarget.dataset.type
    const id = e.currentTarget.dataset.id
    mp.navigateTo({
      url: type === 'post' ? '/pages/post/detail?id=' + id : '/pages/wall/detail?id=' + id
    })
  },

  previewImg: function (e) {
    const urls = e.currentTarget.dataset.urls || []
    const current = e.currentTarget.dataset.current
    if (!urls.length) return
    mp.previewImage({ urls: urls, current: current || urls[0] })
  },

  retry: function () {
    this.load()
  },

  login: function () {
    const self = this
    auth.ensureLogin(true).then(function () {
      self.load()
    }).catch(function () {})
  }
})

/**
 * 空态文案：在 js 里算好再给 wxml —— WXML 里写三层三目一旦括号配错就是整页白屏，
 * 而这种错误编译期只报一行不指向真正原因（2026-10-27 已踩过）。
 */
function emptyTitleOf(tab, cat) {
  if (cat === 'love') return '还没有发过表白'
  if (cat === 'tree') return '还没有发过树洞吐槽'
  if (cat === 'find' || cat === 'findp') return '还没有发过求助'
  if (tab === 'post') return '还没有发过帖子'
  if (tab === 'wall') return '还没有发过树洞'
  return '还没有任何动态'
}

/** 时间、图片、排序时间戳一次算好，wxml 里不做运算 */
function decorate(item) {
  const raw = item.createdAt
  const ts = raw ? new Date(String(raw).replace(/-/g, '/').replace('T', ' ')).getTime() : 0
  item.timeText = timeAgo(raw)
  item._ts = isNaN(ts) ? 0 : ts
  item.imageList = resolveList(item.imageList || [])
  item.id = String(item.id)
  return item
}
