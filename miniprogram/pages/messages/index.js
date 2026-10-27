const http = require('../../utils/request')
const kwUtil = require('../../utils/kw')
const { mp, timeAgo, toast } = require('../../utils/util')
const auth = require('../../utils/auth')

/** 防骗贴士「关掉后不再出现」的本地标记：只存本机，不占后端字段 */
const TIP_CLOSED_KEY = 'msg_tip_closed'

/** 四宫格 → 分包页面（与 subpackages/msg/notice 下四个页面一一对应） */
const NOTICE_PAGE = {
  '赞与收藏': '/subpackages/msg/notice/like',
  '新增粉丝': '/subpackages/msg/notice/fans',
  '评论与@': '/subpackages/msg/notice/comment',
  '官方通知': '/subpackages/msg/notice/system'
}

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
    /** 未登录时为 true：tab 页游客也能点进来，这里给登录引导而不是打一堆 401 */
    needLogin: false,
    filters: [
      { key: 'all', name: '全部消息' },
      { key: 'trade', name: '闲置' },
      { key: 'echo', name: '树洞回声' },
      { key: 'unread', name: '未读消息' }
    ],
    filter: 'all',
    /** 当前处于左滑展开状态的会话 id（受控，保证同时只有一个展开） */
    openSwipeId: '',
    /** 四宫格不再弹抽屉：每类通知都是一个分包页面（subpackages/msg） */
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
      capsuleGap: capsuleGap + 8,
      // 关过就不要再出现：以前只 setData(false)，下次进页面又弹回来
      showTip: !mp.getStorageSync(TIP_CLOSED_KEY)
    })
  },

  /**
   * 本页是 tab 页（底部「私信」），onLoad 只会执行一次，
   * 所以数据刷新统一放在 onShow —— 从聊天页返回、切 tab 回来都要看到最新未读。
   */
  onShow: function () {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      // hidden:false 兜底：弹层若没走正常关闭路径，别让底栏一直不显示
      this.getTabBar().setData({ selected: 3, hidden: false })
    }
    this.load()
  },

  load: function () {
    const self = this
    // 游客进来不打接口：/messages 需要登录，静默 401 只会把登录态清掉
    if (!http.getToken()) {
      this.setData({
        loading: false,
        needLogin: true,
        allConvos: [],
        convos: [],
        summary: { likes: 0, followers: 0, mentions: 0, notice: '' },
        unread: 0
      })
      return
    }
    this.setData({ loading: true, needLogin: false })
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
      self.syncTabUnread(summary.unreadTotal || 0)
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
    // 多词搜索：空格分隔的词都要命中（昵称 / 最后一条消息，任一处命中该词即可）
    list = kwUtil.filterList(list, ['peerName', 'lastText'], kw)
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
    mp.setStorageSync(TIP_CLOSED_KEY, 1)
  },

  /**
   * 快捷入口：四宫格 → 各自的**独立子页面**（分包 subpackages/msg）。
   *
   * 以前这里是同页 bottom-sheet 抽屉：通知一多只能在抽屉里滚，抽屉还会压住自定义
   * tabBar（得手动 toggleTabBar），也没法下拉刷新。现在每类一个页面，支持
   * 下拉刷新 / 上拉分页 / 单页标已读，主包也不再背这堆模板和样式。
   */
  onQuick: function (e) {
    const name = e.currentTarget.dataset.name
    const url = NOTICE_PAGE[name]
    if (!url) return
    // 通知接口都需要登录，游客点进来只会在子页面里撞 401 → 这里先拦住
    if (!http.getToken()) {
      toast('登录后查看' + name)
      this.setData({ needLogin: true })
      return
    }
    mp.navigateTo({ url: url })
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
    // 通知也要一起标：只清会话的话，四宫格（赞 / 粉丝 / 评论@ / 官方）红点还在，
    // 用户刚点「全部已读」却又看到红点，会以为没生效
    http.post('/messages/notifications/read-all', {}, { silent: true }).catch(function () {})
    const cleared = Object.assign({}, this.data.summary, {
      likes: 0, followers: 0, mentions: 0, notice: 0, system: 0,
      unreadNotice: 0, unreadTotal: 0
    })
    this.setData({ summary: cleared })
    this.syncTabUnread(0)
    mp.showToast({ title: '已全部标为已读', icon: 'none' })
  },

  /** 消息设置：跳分包页面（提醒开关 / 免打扰 / 微信服务通知授权） */
  onSettings: function () {
    mp.navigateTo({ url: '/subpackages/msg/settings/index' })
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

  /**
   * 把未读总数同步到底栏「私信」角标。
   *
   * 为什么要手动同步：custom-tab-bar 的 pageLifetimes.show 回调与页面的 onShow
   * 先后不保证，靠底栏自己拉接口可能慢半拍（刚读完消息角标还在）。
   * 这里页面已经拿到 summary.unreadTotal，顺手回写最准。
   */
  syncTabUnread: function (n) {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ unread: Number(n) || 0 })
    }
  },

  /** 游客态：点「去登录」才真正建号（拒绝则继续浏览） */
  goLogin: function () {
    const self = this
    auth.ensureLogin(true).then(function (user) {
      self.setData({ needLogin: false })
      self.load()
    }).catch(function () {})
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
