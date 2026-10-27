/**
 * 我的 · 我发布的需求
 *
 * 与「我发布的闲置」同构：一次拉全量（本人需求量小，服务端无分页），
 * 分段 tab 走本地过滤，切 tab 不发请求 → 秒切。
 */
const http = require('../../utils/request')
const auth = require('../../utils/auth')
const { mp, toast } = require('../../utils/util')
const { resolveList } = require('../../utils/img')
const demandUtil = require('../../utils/demand')
const ui = require('../../utils/ui')

/** 与后端 Demand.STATUS_* 对齐 */
const TABS = [
  { key: 'all', label: '全部' },
  { key: 'open', label: '进行中' },
  { key: 'done', label: '已完成' },
  { key: 'off', label: '已下架' }
]

const OF_STATUS = { open: 1, done: 2, off: 0 }

Page({
  data: {
    tabs: TABS,
    tab: 'all',
    list: [],
    shown: [],
    loading: true,
    loadError: false
  },

  /** 每次进页面都刷一次：从详情页改完状态返回要立刻看到结果 */
  onShow: function () {
    this.load()
  },

  onPullDownRefresh: function () {
    this.load()
  },

  load: function () {
    const self = this
    this.setData({ loadError: false })
    auth.ensureLogin().then(function () {
      return http.get('/demands/mine', null, { silent: true })
    }).then(function (list) {
      self.setData({
        list: (list || []).map(function (x) {
          const it = demandUtil.decorate(x)
          it.imageList = resolveList(it.imageList)
          return it
        }),
        loading: false,
        loadError: false
      })
      self.applyTab(self.data.tab)
      mp.stopPullDownRefresh()
    }).catch(function () {
      self.setData({ loading: false, loadError: true })
      mp.stopPullDownRefresh()
    })
  },

  switchTab: function (e) {
    this.applyTab(e.currentTarget.dataset.key)
  },

  applyTab: function (key) {
    const status = OF_STATUS[key]
    const shown = key === 'all'
      ? this.data.list
      : this.data.list.filter(function (x) { return x.status === status })
    this.setData({ tab: key, shown: shown })
  },

  goDetail: function (e) {
    mp.navigateTo({ url: '/subpackages/demand/detail?id=' + e.currentTarget.dataset.id })
  },

  /** 列表页快捷「已找到」：不用进详情就能收掉一条 */
  quickDone: function (e) {
    const self = this
    const id = e.currentTarget.dataset.id
    ui.confirm({ title: '标记为已找到？', content: '标记后这条需求不会再出现在广场上' })
      .then(function (ok) {
        if (!ok) return
        return http.post('/demands/' + id + '/status', { status: 2 })
      })
      .then(function (ok) {
        if (ok === false) return
        toast('已标记为已完成')
        self.load()
      })
      .catch(function () {})
  },

  goPublish: function () {
    auth.ensureVerified().then(function () {
      mp.navigateTo({ url: '/subpackages/demand/publish' })
    }).catch(function () {})
  }
})
