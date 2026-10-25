const http = require('../../utils/request')
const { mp, timeAgo } = require('../../utils/util')

Page({
  data: {
    loading: true,
    statusBarHeight: 20,
    /** 顶栏右侧预留的胶囊宽度（px），onLoad 实测 */
    capsuleGap: 104,
    summary: { likes: 0, followers: 0, mentions: 0, notice: '' },
    convos: [],
    /** 全量会话（本地筛选，避免每次筛选都打接口） */
    allConvos: [],
    keyword: '',
    showTip: true,
    filters: [
      { key: 'all', name: '全部消息' },
      { key: 'trade', name: '二手交易' },
      { key: 'echo', name: '树洞回声' },
      { key: 'unread', name: '未读消息' }
    ],
    filter: 'all',
    /** 当前处于左滑展开状态的会话 id（受控，保证同时只有一个展开） */
    openSwipeId: '',
    /** 通知抽屉 */
    notifyDrawerOpen: false,
    notifyDrawerTitle: '',
    notifyDrawerItems: [],
    notifyDrawerLoading: false,
    notifyDrawerTypes: []
  },

  onLoad: function () {
    // 自绘顶栏需要自己避开状态栏（navigationStyle:custom）
    const info = typeof mp.getWindowInfo === 'function' ? mp.getWindowInfo() : mp.getSystemInfoSync()
    // 顶栏右侧要给微信胶囊让位：预留「屏幕右缘 → 胶囊左缘」的距离
    let capsuleGap = 96
    try {
      const rect = mp.getMenuButtonBoundingClientRect()
      if (rect && rect.left > 0) {
        capsuleGap = info.windowWidth - rect.left
      }
    } catch (e) { /* 低版本兜底 96px */ }
    this.setData({
      statusBarHeight: info.statusBarHeight || 20,
      capsuleGap: capsuleGap + 8
    })
    this.load()
  },

  load: function () {
    const self = this
    this.setData({ loading: true })
    // 并行拉会话列表 + 通知未读统计
    const convosPromise = http.get('/messages', null, { silent: true }).catch(function () { return {} })
    const notifyPromise = http.get('/messages/notifications/unread', null, { silent: true }).catch(function () { return null })
    Promise.all([convosPromise, notifyPromise]).then(function (results) {
      const res = results[0] || {}
      const unread = results[1] || {}
      const all = (res.convos || []).map(function (c) {
        c.timeText = shortTime(c.lastTime)
        c.id = String(c.id)
        return c
      })
      // 映射通知未读 → summary badge
      const summary = Object.assign({}, self.data.summary, res.summary || {}, {
        likes: unread.like || 0,
        followers: unread.follow || 0,
        mentions: (unread.commentPost || 0) + (unread.commentWall || 0) + (unread.mention || 0),
        notice: unread.system || 0,
        system: unread.system || 0
      })
      self.setData({
        summary: summary,
        allConvos: all,
        loading: false
      })
      self.applyFilter()
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

  /** 本地过滤：数据量小，无需重复请求 */
  applyFilter: function () {
    const kw = (this.data.keyword || '').trim()
    const f = this.data.filter
    let list = this.data.allConvos.slice()
    if (kw) {
      list = list.filter(function (c) {
        return (c.peerName || '').indexOf(kw) >= 0 || (c.lastText || '').indexOf(kw) >= 0
      })
    }
    if (f === 'trade') {
      list = list.filter(function (c) { return !!c.goodsCard })
    } else if (f === 'echo') {
      list = list.filter(function (c) { return !!c.anon })
    } else if (f === 'unread') {
      list = list.filter(function (c) { return c.unread > 0 })
    }
    this.setData({ convos: list })
  },

  closeTip: function () {
    this.setData({ showTip: false })
  },

  /** 快捷入口：点分类 badge → 打开 bottom-sheet 通知列表 */
  onQuick: function (e) {
    const self = this
    const name = e.currentTarget.dataset.name
    const typeMap = {
      '赞与收藏': ['LIKE_GOODS', 'LIKE_POST', 'LIKE_WALL'],
      '新增粉丝': ['NEW_FOLLOWER'],
      '评论与@': ['COMMENT_POST', 'COMMENT_WALL', 'COMMENT_GOODS', 'MENTION'],
      '官方通知': ['SYSTEM']
    }
    const types = typeMap[name] || []
    this.setData({ notifyDrawerTitle: name, notifyDrawerTypes: types, notifyDrawerItems: [], notifyDrawerOpen: true, notifyDrawerLoading: true })
    // 拉全部类型，前端过滤（后端无多 type OR 查询，先拉全量）
    http.get('/messages/notifications?size=30', null, { silent: true }).then(function (res) {
      const items = (res.records || [])
        .filter(function (n) { return types.indexOf(n.type) >= 0 })
        .map(function (n) {
          n.timeText = shortTime(n.createdAt)
          n.unread = Number(n.isRead) === 0
          return n
        })
      self.setData({ notifyDrawerItems: items, notifyDrawerLoading: false })
    }).catch(function () {
      self.setData({ notifyDrawerLoading: false })
      mp.showToast({ title: '加载失败', icon: 'none' })
    })
  },

  closeNotifyDrawer: function () {
    this.setData({ notifyDrawerOpen: false })
  },

  /** 抽屉内「全部标为已读」 */
  onMarkCurrentRead: function () {
    const self = this
    const types = this.data.notifyDrawerTypes
    Promise.all(types.map(function (t) {
      return http.post('/messages/notifications/read/' + t, null, { silent: true })
    })).then(function () {
      const items = self.data.notifyDrawerItems.map(function (n) { n.unread = false; return n })
      self.setData({ notifyDrawerItems: items })
      self.load()
      mp.showToast({ title: '已全部标为已读', icon: 'none' })
    })
  },

  /** 点通知卡片跳关联详情 */
  goNotifyDetail: function (e) {
    const item = this.data.notifyDrawerItems[e.currentTarget.dataset.index]
    if (!item) return
    // 自动标记已读（乐观更新）
    const items = this.data.notifyDrawerItems
    items[e.currentTarget.dataset.index].unread = false
    this.setData({ notifyDrawerItems: items })
    // 后端标记单条
    http.post('/messages/notifications/read/' + item.type, null, { silent: true })
    // 路由跳转
    let url = ''
    const targetId = item.targetId
    const actorId = item.actorId
    switch (item.targetType) {
      case 'GOODS': url = '/pages/goods/detail?id=' + targetId; break
      case 'POST': url = '/pages/post/detail?id=' + targetId; break
      case 'WALL': url = '/pages/wall/detail?id=' + targetId; break
      case 'FOCUS': url = '/pages/user/space/space?id=' + actorId; break
      default: break
    }
    if (url) mp.navigateTo({ url: url })
  },

  markAllRead: function () {
    const self = this
    const unread = this.data.convos.filter(function (c) { return c.unread > 0 })
    const patch = {}
    this.data.allConvos.forEach(function (c, i) {
      if (c.unread > 0) patch['allConvos[' + i + '].unread'] = 0
    })
    this.setData(patch)
    this.applyFilter()
    // 同步到后端：逐个会话标记已读，失败不影响本地已清零的展示
    unread.forEach(function (c) {
      http.post('/messages/' + c.id + '/read', null, { silent: true }).catch(function () {})
    })
    mp.showToast({ title: '已全部标为已读', icon: 'none' })
  },

  onSettings: function () {
    mp.showToast({ title: '消息设置即将上线', icon: 'none' })
  },

  /** 左滑卡片展开/收起：受控，保证同时只有一个展开 */
  onSwipeChange: function (e) {
    const id = e.currentTarget.dataset.id
    const open = e.detail.open
    this.setData({ openSwipeId: open ? id : (this.data.openSwipeId === id ? '' : this.data.openSwipeId) })
  },

  /** 左滑操作：删除会话 */
  onSwipeAction: function (e) {
    const id = e.currentTarget.dataset.id
    const self = this
    const convo = this.data.allConvos.filter(function (c) { return c.id === id })[0]
    const name = convo ? convo.peerName : '该会话'
    mp.showModal({
      title: '删除会话',
      content: '确定删除与「' + name + '」的会话记录吗？删除后聊天内容将不再展示，对方不受影响。',
      confirmText: '删除',
      confirmColor: '#ef4444',
      success: function (res) {
        if (!res.confirm) return
        self.doDelete(id)
      }
    })
  },

  doDelete: function (id) {
    const self = this
    http.del('/messages/' + id, null, { silent: true }).then(function () {
      const all = self.data.allConvos.filter(function (c) { return c.id !== id })
      self.setData({ allConvos: all, openSwipeId: '' })
      self.applyFilter()
      mp.showToast({ title: '已删除会话', icon: 'none' })
    }).catch(function () {
      mp.showToast({ title: '删除失败，请重试', icon: 'none' })
    })
  },

  goChat: function (e) {
    const id = e.currentTarget.dataset.id
    const name = e.currentTarget.dataset.name
    // 卡片处于左滑展开态时，点击收起而不是进入聊天
    if (this.data.openSwipeId === id) {
      this.setData({ openSwipeId: '' })
      return
    }
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
