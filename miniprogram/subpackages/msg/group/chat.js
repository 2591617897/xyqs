const http = require('../../../utils/request')
const ws = require('../utils/ws')
const { mp, toast } = require('../../../utils/util')

/** 轮询兜底间隔：WebSocket 降级后每 8 秒拉一次增量 */
const POLL_MS = 8000
/** 撤回窗口（毫秒）：与后端 RECALL_WINDOW_MS 保持一致，2 分钟 */
const RECALL_MS = 2 * 60 * 1000

Page({
  data: {
    statusBarHeight: 20,
    capsuleGap: 104,
    groupId: '',
    group: null,
    noticeOpen: false,
    msgs: [],
    myId: '',
    inputText: '',
    /** 键盘高度：底部输入栏用 adjust-position=false 自己抬，避免整页被顶飞 */
    keyboardHeight: 0,
    canSend: true,
    blockReason: '',
    nightMute: false,
    showRules: false,
    degraded: false,
    atUsers: [],
    atAll: false,
    atAllAllowed: false,
    /** 正在回复的消息 { id, name, text }；null = 普通发言 */
    replyTo: null,
    scrollInto: '',
    loading: true,
    /** 我是否已确认群规（确认后才允许发第一条） */
    needAgree: false
  },

  // ============ 生命周期 ============

  onLoad: function (opt) {
    const self = this
    const info = typeof mp.getWindowInfo === 'function' ? mp.getWindowInfo() : mp.getSystemInfoSync()
    let capsuleGap = 96
    try {
      const rect = mp.getMenuButtonBoundingClientRect()
      if (rect && rect.left > 0) capsuleGap = info.windowWidth - rect.left
    } catch (e) { /* 低版本兜底 */ }

    const me = mp.getStorageSync('user') || {}
    this.setData({
      statusBarHeight: info.statusBarHeight || 20,
      capsuleGap: capsuleGap + 8,
      myId: me.id ? String(me.id) : '',
      // 自己的头像：历史消息走后端 refreshAuthors 覆盖，但本地乐观消息与实时推送
      // 的 senderAvatar 是空的，必须用本地缓存兜底，否则自己那侧永远是个灰头像
      myAvatar: me.avatar || ''
    })

    // 本地缓存没头像（老账号 / 换设备登录）时补拉一次资料，
    // 否则自己那侧会一直是个灰头像，用户以为头像功能坏了
    if (!this.data.myAvatar) this.syncMyAvatar()

    if (opt && opt.id) {
      this.setData({ groupId: String(opt.id) })
      this.bootstrap()
    } else {
      // 从消息页直接进来没带 id：用「我的群」兜底
      http.get('/groups/mine', null, { silent: true }).then(function (res) {
        const g = res && res.group
        if (!g) {
          toast('还没有可进入的校友群')
          setTimeout(function () { mp.navigateBack() }, 1200)
          return
        }
        self.setData({ groupId: String(g.id) })
        self.bootstrap()
      }).catch(function () {
        self.setData({ loading: false })
      })
    }
  },

  onShow: function () {
    if (this.data.groupId && !this.data.loading) {
      // 从成员页 / 设置页返回：刷新群状态（可能被设成管理员、被禁言）
      this.loadDetail()
      this.reportRead()
      ws.connect()
    }
    // 自定义 tabBar 兜底（本页非 tab，但保险）
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ hidden: false })
    }
  },

  onHide: function () {
    this.stopPolling()
  },

  onUnload: function () {
    this.stopPolling()
    // 先注销回调再断连：不注销的话页面销毁后闭包还挂在 ws 模块上，
    // 下次进群会收到两份回调（同一条消息渲染两次），并且内存不释放（E-09）
    this.unbindWs()
    this._bound = false
    if (this.data.groupId) ws.leave(this.data.groupId)
    // 页面退出即断开：后台挂着长连接只会耗电，用户切回来会重连 + 补拉
    ws.shutdown()
  },

  // ============ 初始化 ============

  bootstrap: function () {
    const self = this
    this.setData({ loading: true })
    Promise.all([
      http.get('/groups/' + this.data.groupId, null, { silent: true }).catch(function () { return null }),
      http.get('/groups/' + this.data.groupId + '/messages', { limit: 30 }, { silent: true }).catch(function () { return [] })
    ]).then(function (res) {
      const detail = res[0]
      const list = res[1] || []
      if (!detail) {
        toast('群不存在或你不在群里')
        setTimeout(function () { mp.navigateBack() }, 1200)
        return
      }
      self.applyDetail(detail)
      const msgs = self.decorate(list, false)
      self.setData({
        msgs: msgs,
        loading: false,
        needAgree: !detail.rulesAgreed,
        showRules: !detail.rulesAgreed,
        scrollInto: msgs.length ? 'm-last' : ''
      })
      self.bindWs()
      ws.connect()
      self.reportRead()
    })
  },

  loadDetail: function () {
    const self = this
    http.get('/groups/' + this.data.groupId, null, { silent: true }).then(function (d) {
      if (d) self.applyDetail(d)
    }).catch(function () {})
  },

  applyDetail: function (d) {
    const atAllAllowed = !!d.atAllAllowed
    this.setData({
      group: d,
      canSend: !!d.canSend,
      blockReason: d.blockReason || '',
      nightMute: !!d.nightMute,
      atAllAllowed: atAllAllowed,
      atAll: atAllAllowed ? this.data.atAll : false
    })
  },

  /**
   * 补拉自己的头像。
   *
   * <p>本地缓存（storage.user）可能是旧版本写的、或者压根没存头像，
   * 这时「我」的消息只能显示灰头像 —— 用户看到的现象就是「自己的消息没有头像」。
   * 一次 /user/me 换正确渲染，比让用户去资料页改一遍头像划算。
   */
  syncMyAvatar: function () {
    const self = this
    http.get('/user/me', null, { silent: true }).then(function (u) {
      const avatar = (u && u.avatar) || ''
      if (!avatar) return
      self.setData({ myAvatar: avatar })
      const msgs = (self.data.msgs || []).map(function (m) {
        return m.mine && !m.senderAvatar ? Object.assign({}, m, { senderAvatar: avatar }) : m
      })
      self.setData({ msgs: msgs })
    }).catch(function () {})
  },

  // ============ WebSocket 事件 ============

  bindWs: function () {
    const self = this
    if (this._bound) return
    this._bound = true

    this._onMsg = function (frame) {
      self.appendMessage(frame.message)
    }
    this._onDelete = function (frame) {
      const id = String(frame.messageId || '')
      const msgs = self.data.msgs.map(function (m) {
        return String(m.id) === id ? Object.assign({}, m, { deleted: 1 }) : m
      })
      self.setData({ msgs: msgs })
    }
    // 别人撤回（或自己在别的设备撤回）：和「管理员删除」文案不同
    this._onRecall = function (frame) {
      self.markRecalled(frame.messageId)
    }
    this._onKick = function () {
      toast('你已被移出本群')
      setTimeout(function () { mp.navigateBack() }, 1200)
    }
    this._onMute = function () {
      self.loadDetail()
    }
    this._onRole = function () {
      self.loadDetail()
    }
    this._onError = function (frame) {
      toast(frame.msg || '操作失败')
      // 被限流 / 被禁言：刷新一次状态，让输入框立刻显示正确原因
      if (frame.code === 403 || frame.code === 429) self.loadDetail()
      self.markLocalFailed()
    }
    this._onOpen = function () {
      self.setData({ degraded: false })
      self.stopPolling()
      // ★ 重连后补拉：这是「不漏消息」的唯一保证
      self.pullSince()
      self.reportRead()
    }
    this._onDegrade = function () {
      // 连续 3 次连不上 → 切 HTTP 轮询，保证弱网下功能不瘫
      self.setData({ degraded: true })
      self.startPolling()
    }

    ws.on('msg', this._onMsg)
    ws.on('delete', this._onDelete)
    ws.on('recall', this._onRecall)
    ws.on('kick', this._onKick)
    ws.on('mute', this._onMute)
    ws.on('unmute', this._onMute)
    ws.on('role', this._onRole)
    ws.on('error', this._onError)
    ws.on('open', this._onOpen)
    ws.on('degrade', this._onDegrade)

    // 建立连接后订阅本群（重连也会自动恢复）
    ws.join(this.data.groupId)
  },

  unbindWs: function () {
    ws.off('msg', this._onMsg)
    ws.off('delete', this._onDelete)
    ws.off('recall', this._onRecall)
    ws.off('kick', this._onKick)
    ws.off('mute', this._onMute)
    ws.off('unmute', this._onMute)
    ws.off('role', this._onRole)
    ws.off('error', this._onError)
    ws.off('open', this._onOpen)
    ws.off('degrade', this._onDegrade)
  },

  // ============ 轮询兜底 ============

  startPolling: function () {
    const self = this
    if (this._poll) return
    this._poll = setInterval(function () {
      if (ws.isOpen()) {
        self.stopPolling()
        return
      }
      self.pullSince()
    }, POLL_MS)
  },

  stopPolling: function () {
    if (this._poll) {
      clearInterval(this._poll)
      this._poll = null
    }
  },

  // ============ 消息 ============

  /** 本地消息形状 → 展示形状（时间文案、是否我发的、@ 文案） */
  decorate: function (list, isAppend) {
    const myId = this.data.myId
    const myAvatar = this.data.myAvatar || ''
    return list.map(function (m) {
      // 先定「是不是我发的」，头像要用它兜底：我自己发的消息后端 senderAvatar 常为空
      const mine = myId ? String(m.senderId) === myId : !!m.self
      return {
        id: String(m.id),
        clientMsgId: m.clientMsgId || '',
        senderId: String(m.senderId),
        senderName: m.senderName || '',
        senderAvatar: m.senderAvatar || (mine ? myAvatar : ''),
        content: m.content,
        type: m.type || 'text',
        atAll: !!m.atAll,
        atText: m.atAll ? '所有人' : '',
        // 引用回复：服务端存快照，跳转用 replyToId（原消息不在列表里就只显示文字）
        replyToId: m.replyToId ? String(m.replyToId) : '',
        replyToName: m.replyToName || '',
        replyToText: m.replyToText || '',
        deleted: m.deleted || 0,
        // 1 本人撤回 / 2 管理员删除：文案不同，所以不能只看 deleted
        deletedBy: m.deletedBy || 0,
        recalled: Number(m.deletedBy) === 1,
        createdAt: m.createdAt || '',
        timeText: shortTime(m.createdAt),
        mine: mine,
        pending: false,
        failed: false
      }
    })
  },

  appendMessage: function (msg) {
    if (!msg) return
    const list = this.data.msgs.slice()
    const incoming = this.decorate([msg], true)[0]

    // 自己发的消息：按 clientMsgId 替换本地乐观气泡，绝不追加（否则一条显示两次）
    if (incoming.clientMsgId) {
      for (let i = list.length - 1; i >= 0; i--) {
        if (list[i].clientMsgId && list[i].clientMsgId === incoming.clientMsgId) {
          list[i] = incoming
          this.setData({ msgs: list, scrollInto: 'm-last' })
          this.reportRead()
          return
        }
      }
    }
    // 已有同 id（补拉与 WS 同时到达）→ 忽略
    for (let i = 0; i < list.length; i++) {
      if (list[i].id === incoming.id) return
    }
    list.push(incoming)
    this.setData({ msgs: list.slice(-200), scrollInto: 'm-last' })
    this.reportRead()
  },

  /** 增量补拉（断线重连 / 轮询兜底都走这里） */
  pullSince: function () {
    const self = this
    const msgs = this.data.msgs
    let sinceId = 0
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].id && String(msgs[i].id).indexOf('local-') !== 0) {
        sinceId = msgs[i].id
        break
      }
    }
    if (!sinceId) return
    http.get('/groups/' + this.data.groupId + '/messages',
      { sinceId: sinceId, limit: 50 }, { silent: true }
    ).then(function (list) {
      if (!list || !list.length) return
      const fresh = self.decorate(list, true)
      const merged = self.data.msgs.slice()
      fresh.forEach(function (m) {
        let dup = false
        for (let i = 0; i < merged.length; i++) {
          if (merged[i].id === m.id || (m.clientMsgId && merged[i].clientMsgId === m.clientMsgId)) {
            merged[i] = m
            dup = true
            break
          }
        }
        if (!dup) merged.push(m)
      })
      self.setData({ msgs: merged.slice(-200), scrollInto: 'm-last' })
      self.reportRead()
    }).catch(function () {})
  },

  onInput: function (e) {
    this.setData({ inputText: e.detail.value })
  },

  onKeyboard: function (e) {
    this.setData({ keyboardHeight: (e.detail && e.detail.height) || 0 })
  },

  onSend: function () {
    const text = (this.data.inputText || '').trim()
    if (!text) return
    if (this.data.needAgree) {
      this.setData({ showRules: true })
      return
    }
    if (!this.data.canSend) {
      toast(this.data.blockReason || '当前无法发言')
      return
    }
    const clientMsgId = genId()
    const reply = this.data.replyTo
    const local = {
      id: 'local-' + clientMsgId,
      clientMsgId: clientMsgId,
      senderId: this.data.myId,
      senderName: '我',
      senderAvatar: this.data.myAvatar || '',
      content: text,
      type: 'text',
      atAll: this.data.atAll,
      atText: this.data.atAll ? '所有人' : '',
      replyToId: reply ? reply.id : '',
      replyToName: reply ? reply.name : '',
      replyToText: reply ? reply.text : '',
      deleted: 0,
      deletedBy: 0,
      recalled: false,
      createdAt: '',
      timeText: '发送中',
      mine: true,
      pending: true,
      failed: false
    }
    this.setData({
      msgs: this.data.msgs.concat([local]),
      inputText: '',
      atUsers: [],
      atAll: false,
      replyTo: null,
      scrollInto: 'm-last'
    })

    const payload = {
      msgType: 'text',
      content: text,
      clientMsgId: clientMsgId,
      atUserIds: (this.data.atUsers || []).map(function (u) { return String(u.id) }),
      atAll: local.atAll,
      replyToId: reply ? reply.id : ''
    }

    const sent = ws.sendMsg(this.data.groupId, payload)
    if (!sent) {
      // WS 不可用 → 立刻走 HTTP，用户不该因为长连接断了就发不出消息
      this.sendByHttp(payload)
    }
  },

  sendByHttp: function (payload) {
    const self = this
    http.post('/groups/' + this.data.groupId + '/messages', {
      type: payload.msgType,
      content: payload.content,
      clientMsgId: payload.clientMsgId,
      atUserIds: payload.atUserIds,
      atAll: payload.atAll,
      replyToId: payload.replyToId || ''
    }, { silent: true }).then(function (msg) {
      // 用服务端返回替换本地气泡（WS 那条可能也会到，靠 clientMsgId 去重）
      self.appendMessage(msg)
    }).catch(function (err) {
      self.markLocalFailed()
      toast((err && err.msg) || '发送失败，请重试')
      if (err && (err.code === 403 || err.code === 429)) self.loadDetail()
    })
  },

  markLocalFailed: function () {
    const msgs = this.data.msgs.map(function (m) {
      return m.pending ? Object.assign({}, m, { pending: false, failed: true, timeText: '发送失败' }) : m
    })
    this.setData({ msgs: msgs })
  },

  /** 上报已读游标（同时清 @我 的未读） */
  reportRead: function () {
    const msgs = this.data.msgs
    for (let i = msgs.length - 1; i >= 0; i--) {
      const id = String(msgs[i].id || '')
      if (id && id.indexOf('local-') !== 0) {
        http.post('/groups/' + this.data.groupId + '/read', { lastReadId: id }, { silent: true })
          .catch(function () {})
        return
      }
    }
  },

  // ============ 交互 ============

  goBack: function () {
    mp.navigateBack()
  },

  toggleNotice: function () {
    this.setData({ noticeOpen: !this.data.noticeOpen })
  },

  agreeRules: function () {
    const self = this
    http.post('/groups/' + this.data.groupId + '/agree-rules', null, { silent: true }).then(function () {
      self.setData({ showRules: false, needAgree: false })
      const g = Object.assign({}, self.data.group, { rulesAgreed: true })
      self.setData({ group: g })
    }).catch(function () {
      // 网络失败也放行本地：真正的确认状态以服务端为准，下次进来还会再问
      self.setData({ showRules: false, needAgree: false })
    })
  },

  /** 群规层：未确认时不允许关闭（要用户真的看完） */
  noop: function () {},

  goSetting: function () {
    mp.navigateTo({ url: '/subpackages/msg/group/setting?id=' + this.data.groupId })
  },

  // ============ 消息操作：长按菜单 / 撤回 / 回复 ============

  /**
   * 长按气泡出操作菜单。
   *
   * <p>菜单项按可用性动态拼：撤回只在「自己发的 + 2 分钟内 + 已落库」时出现，
   * 举报只对别人的消息出现 —— 把不能用的选项灰着放上去，用户点了才发现不行，
   * 比直接不显示更糟。
   */
  onMsgLong: function (e) {
    const idx = e.currentTarget.dataset.idx
    const m = (this.data.msgs || [])[idx]
    if (!m) return

    const actions = []
    if (!m.deleted) actions.push({ key: 'reply', label: '回复' })
    if (this.canRecall(m)) actions.push({ key: 'recall', label: '撤回' })
    if (!m.deleted && m.type === 'text' && m.content) actions.push({ key: 'copy', label: '复制' })
    if (!m.deleted && !m.mine) actions.push({ key: 'report', label: '举报' })
    if (!actions.length) return

    const self = this
    mp.showActionSheet({
      itemList: actions.map(function (a) { return a.label }),
      success: function (res) {
        const act = actions[res.tapIndex]
        if (act) self.doMsgAction(act.key, m)
      },
      fail: function () { /* 用户取消，不需要提示 */ }
    })
  },

  /** 能否撤回：前端只做「要不要显示入口」的粗判，最终以服务端判定为准 */
  canRecall: function (m) {
    if (!m || m.deleted || !m.mine) return false
    // 「发送中」的本地消息还没有服务端 id，撤回没有意义
    if (!m.id || String(m.id).indexOf('local-') === 0) return false
    const t = parseTime(m.createdAt)
    if (!t) return true   // 解析不出来就交给服务端判定，别因为前端算不出就不给入口
    return Date.now() - t <= RECALL_MS
  },

  doMsgAction: function (key, m) {
    if (key === 'reply') {
      this.replyToMsg(m)
      return
    }
    if (key === 'copy') {
      mp.setClipboardData({
        data: m.content || '',
        success: function () { toast('已复制') }
      })
      return
    }
    if (key === 'recall') {
      this.recallMsg(m)
      return
    }
    if (key === 'report') {
      mp.navigateTo({ url: '/pages/report/report?type=GROUP_MSG&id=' + m.id })
    }
  },

  /** 撤回：二次确认 + 真调接口（错误原因原样透出，例如「超过 2 分钟的消息无法撤回」） */
  recallMsg: function (m) {
    const self = this
    mp.showModal({
      title: '撤回这条消息',
      content: '撤回后群里所有人都看不到内容，且不可恢复。',
      confirmText: '撤回',
      confirmColor: '#B2311A',
      success: function (res) {
        if (!res.confirm) return
        http.post('/groups/' + self.data.groupId + '/messages/' + m.id + '/recall', null,
          { loading: '撤回中' }
        ).then(function () {
          self.markRecalled(m.id)
          toast('已撤回')
        }).catch(function (err) {
          toast((err && err.msg) || '撤回失败，请重试')
        })
      }
    })
  },

  /** 本地标记撤回（服务端广播到达前先更新自己的界面，避免自己还以为没撤） */
  markRecalled: function (id) {
    const target = String(id || '')
    if (!target) return
    const msgs = this.data.msgs.map(function (m) {
      if (String(m.id) !== target) return m
      return Object.assign({}, m, { deleted: 1, deletedBy: 1, recalled: true })
    })
    this.setData({ msgs: msgs })
  },

  /** 引用某条消息：只记 id + 展示用文案，真正发出去时服务端重新取快照 */
  replyToMsg: function (m) {
    this.setData({
      replyTo: {
        id: String(m.id),
        name: m.mine ? '我' : (m.senderName || '校友'),
        text: m.type === 'image' ? '[图片]' : clipText(m.content || '', 40)
      }
    })
  },

  cancelReply: function () {
    this.setData({ replyTo: null })
  },

  /** 点引用块跳到原消息；不在当前页（更早的历史）就提示一下，不做无反馈的静默失败 */
  jumpTo: function (e) {
    const id = String(e.currentTarget.dataset.id || '')
    if (!id) return
    const exists = (this.data.msgs || []).some(function (m) { return String(m.id) === id })
    if (!exists) {
      toast('原消息不在当前列表里，上滑翻一下')
      return
    }
    const self = this
    // 同一个锚点连续点两次不会再触发滚动，必须先清空
    this.setData({ scrollInto: '' })
    setTimeout(function () { self.setData({ scrollInto: 'm-' + id }) }, 30)
  },

  /** 打开成员选择器（@ 用），选中结果通过 eventChannel 回来 */
  openAtPicker: function () {
    const self = this
    mp.navigateTo({
      url: '/subpackages/msg/group/members?id=' + this.data.groupId + '&select=1',
      success: function (res) {
        res.eventChannel.on('pickMember', function (user) {
          if (!user) return
          self.addAt(user)
        })
      }
    })
  },

  /**
   * 把一个成员加入 @ 列表并写进输入框。
   *
   * <p>两条入口共用：成员选择器 + 长按头像（长按头像是最快的方式，
   * 不用先去列表里找人）。
   */
  addAt: function (user) {
    if (!user || !user.id) return
    const name = user.nickname || '校友'
    const list = (this.data.atUsers || []).slice()
    if (list.some(function (u) { return String(u.id) === String(user.id) })) {
      toast('已经 @ 过 ' + name + ' 了')
      return
    }
    if (list.length >= 10) {
      toast('单条消息最多 @ 10 人')
      return
    }
    list.push({ id: String(user.id), nickname: name })
    this.setData({
      atUsers: list,
      inputText: (this.data.inputText || '') + '@' + name + ' '
    })
    toast('已 @ ' + name)
  },

  removeAt: function (e) {
    const idx = e.currentTarget.dataset.idx
    const list = (this.data.atUsers || []).slice()
    list.splice(idx, 1)
    this.setData({ atUsers: list })
  },

  toggleAtAll: function () {
    if (!this.data.atAllAllowed) {
      toast('只有群管理员可以 @所有人')
      return
    }
    this.setData({ atAll: !this.data.atAll })
  },

  /** 点消息里的头像 → 资料卡 / 私聊 */
  onAvatar: function (e) {
    const uid = e.currentTarget.dataset.uid
    if (!uid || String(uid) === this.data.myId) return
    mp.navigateTo({ url: '/pages/user/space/space?id=' + uid })
  },

  /**
   * 长按头像 → 直接 @ 这个人。
   *
   * <p>群成员多的时候，从「@ 按钮 → 成员列表 → 找人」要三步，
   * 长按头像一步到位 —— 这也是微信里最顺手的一条路径。
   */
  onAvatarLong: function (e) {
    const uid = String(e.currentTarget.dataset.uid || '')
    const name = e.currentTarget.dataset.name || '校友'
    if (!uid) return
    if (uid === this.data.myId) {
      toast('不能 @ 自己')
      return
    }
    this.addAt({ id: uid, nickname: name })
  }
})

/** 消息时间：今天只显示 HH:mm，跨天显示 月-日 HH:mm */
function shortTime(t) {
  if (!t) return ''
  const d = new Date(String(t).replace(/-/g, '/'))
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const hm = pad2(d.getHours()) + ':' + pad2(d.getMinutes())
  if (d.toDateString() === now.toDateString()) return hm
  return (d.getMonth() + 1) + '-' + d.getDate() + ' ' + hm
}

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

/** 后端时间串 → 毫秒时间戳（兼容 - / T / 毫秒后缀），解析失败返回 0 */
function parseTime(t) {
  if (!t) return 0
  const d = new Date(String(t).replace(/-/g, '/').replace('T', ' ').replace(/\.\d+$/, ''))
  return isNaN(d.getTime()) ? 0 : d.getTime()
}

/** 引用块只显示一行摘要，太长会撑破气泡 */
function clipText(s, n) {
  const t = String(s || '').replace(/\s+/g, ' ').trim()
  return t.length > n ? t.slice(0, n) + '…' : t
}

/** 客户端幂等 id（不依赖 crypto，低版本基础库也能跑） */
function genId() {
  return 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
}
