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

Page({
  data: {
    tabs: [],
    tab: 'all',
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
    if (!key || key === this.data.tab) return
    this.applyTab(key)
  },

  applyTab: function (key) {
    const master = this.data.master
    const countOf = function (k) {
      if (k === 'all') return master.length
      return master.filter(function (it) { return it._type === k }).length
    }
    this.setData({
      tab: key,
      tabs: TABS.map(function (t) {
        return { key: t.key, label: t.label, count: countOf(t.key) }
      }),
      shown: key === 'all' ? master : master.filter(function (it) { return it._type === key })
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
