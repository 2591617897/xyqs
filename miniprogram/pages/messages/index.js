const http = require('../../utils/request')
const { mp, timeAgo } = require('../../utils/util')

Page({
  data: {
    loading: true,
    summary: { likes: 0, followers: 0, mentions: 0, notice: '' },
    convos: [],
    keyword: '',
    showTip: true,
    filters: [
      { key: 'all', name: '全部消息' },
      { key: 'trade', name: '二手交易' },
      { key: 'echo', name: '树洞回声' },
      { key: 'unread', name: '未读消息' }
    ],
    filter: 'all'
  },

  onLoad: function () {
    mp.setNavigationBarTitle({ title: '私信与消息中心' })
    this.load()
  },

  load: function () {
    const self = this
    this.setData({ loading: true })
    http.get('/messages', null, { silent: true }).then(function (res) {
      const convos = (res.convos || []).map(function (c) {
        c.timeText = shortTime(c.lastTime)
        return c
      })
      self.setData({
        summary: res.summary || self.data.summary,
        convos: convos,
        loading: false
      })
    }).catch(function () {
      self.setData({ loading: false })
    })
  },

  onSearch: function (e) {
    this.setData({ keyword: e.detail.value })
    this.applyFilter()
  },

  switchFilter: function (e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.filter) return
    this.setData({ filter: key })
    this.applyFilter()
  },

  applyFilter: function () {
    const self = this
    const kw = (this.data.keyword || '').trim()
    http.get('/messages', null, { silent: true }).then(function (res) {
      let list = (res.convos || []).map(function (c) {
        c.timeText = shortTime(c.lastTime)
        return c
      })
      if (kw) {
        list = list.filter(function (c) {
          return c.peerName.indexOf(kw) >= 0 || (c.lastText || '').indexOf(kw) >= 0
        })
      }
      const f = self.data.filter
      if (f === 'trade') {
        list = list.filter(function (c) { return !!c.goodsCard })
      } else if (f === 'echo') {
        list = list.filter(function (c) { return !!c.anon })
      } else if (f === 'unread') {
        list = list.filter(function (c) { return c.unread > 0 })
      }
      self.setData({ convos: list })
    }).catch(function () {})
  },

  closeTip: function () {
    this.setData({ showTip: false })
  },

  /** 快捷入口（赞与收藏等）：演示占位 */
  onQuick: function (e) {
    mp.showToast({ title: '「' + e.currentTarget.dataset.name + '」即将上线', icon: 'none' })
  },

  markAllRead: function () {
    const self = this
    const unread = this.data.convos.filter(function (c) { return c.unread > 0 })
    const patch = {}
    this.data.convos.forEach(function (c, i) {
      patch['convos[' + i + '].unread'] = 0
    })
    this.setData(patch)
    // 同步到后端：逐个会话标记已读，失败不影响本地已清零的展示
    unread.forEach(function (c) {
      http.post('/messages/' + c.id + '/read', null, { silent: true }).catch(function () {})
    })
    mp.showToast({ title: '已全部标为已读', icon: 'none' })
  },

  onSettings: function () {
    mp.showToast({ title: '消息设置即将上线', icon: 'none' })
  },

  goChat: function (e) {
    const id = e.currentTarget.dataset.id
    const name = e.currentTarget.dataset.name
    mp.navigateTo({ url: '/pages/chat/index?id=' + id + '&name=' + encodeURIComponent(name || '') })
  },

  goBack: function () {
    mp.navigateBack()
  }
})

/** 会话时间：今天显示 HH:MM，更早用相对时间 */
function shortTime(t) {
  if (!t) return ''
  const d = new Date(String(t).replace(/-/g, '/'))
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return pad2(d.getHours()) + ':' + pad2(d.getMinutes())
  }
  const yesterday = new Date(now.getTime() - 86400000)
  if (d.toDateString() === yesterday.toDateString()) {
    return '昨天 ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes())
  }
  return timeAgo(t)
}

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}
