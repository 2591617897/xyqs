const http = require('../../../utils/request')
const ws = require('../utils/ws')
const upload = require('../../../utils/upload')
const { mp, toast } = require('../../../utils/util')
const auth = require('../../../utils/auth')

/** 轮询兜底间隔：WebSocket 降级后每 8 秒拉一次增量 */
const POLL_MS = 8000
/** 撤回窗口（毫秒）：与后端 RECALL_WINDOW_MS 保持一致，2 分钟 */
const RECALL_MS = 2 * 60 * 1000
/** 单条消息最多 @ 的人数：与后端 GroupService.MAX_AT_USER 对齐 */
const MAX_AT = 10
/**
 * 时间分隔条的聚合间隔（毫秒）。
 *
 * <p>QQ / 微信都不是「每条消息下面挂一个时间」，而是相隔够久才插一条居中的时间：
 * 5 分钟内连续聊天不重复打扰，超过 5 分钟给用户一个「换了一段对话」的锚点。
 */
const TIME_GAP_MS = 5 * 60 * 1000

/**
 * 表情面板数据。
 *
 * <p>只用 Unicode 表情（不引图片包）：iOS / Android 都能渲染，
 * 不占分包体积，也不会出现「图片 404 = 一片空白格子」。
 * 每个数组内不得有重复项 —— wxml 用 *this 做 key，重复会告警。
 */
const EMOJI_GROUPS = [
  {
    name: '笑脸',
    list: [
      '😀', '😄', '😁', '😆', '😅', '😂', '🤣', '😊',
      '😇', '🙂', '😉', '😍', '🥰', '😘', '😜', '🤪',
      '🤨', '🧐', '🤓', '😎', '🥳', '😏', '😒', '😞',
      '😔', '😟', '😕', '🙁', '😣', '😖', '😫', '😩',
      '🥺', '😢', '😭', '😤', '😠', '😡', '🤬', '😱',
      '😨', '😰', '😥', '😓', '🤗', '🤔', '🤭', '🤫',
      '😴', '🤤', '😪', '😵', '🤯', '🥱', '😷', '🤒'
    ]
  },
  {
    name: '手势',
    list: [
      '👍', '👎', '👌', '✌️', '🤞', '🤟', '🤘', '🤙',
      '👈', '👉', '👆', '👇', '☝️', '✋', '🤚', '🖐️',
      '🖖', '👋', '🤝', '🙏', '💪', '👏', '🙌', '🤲'
    ]
  },
  {
    name: '心情',
    list: [
      '❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍',
      '💔', '❣️', '💕', '💞', '💓', '💗', '💖', '💘',
      '💝', '💟', '✨', '⭐', '🌟', '💫', '🔥', '💯',
      '🎉', '🎊', '🎁', '🎈', '🎂', '🌸', '🌹', '🌻'
    ]
  },
  {
    name: '生活',
    list: [
      '🍚', '🍜', '🍲', '🍱', '🍔', '🍟', '🍕', '🍗',
      '🍖', '🍰', '🍦', '🍎', '🍊', '🍉', '🍓', '🥤',
      '☕', '🍺', '🥛', '🍞', '🌙', '☀️', '🌈', '⚡',
      '❄️', '🌊', '🌿', '🍀', '🐱', '🐶', '🐼', '🐰'
    ]
  }
]

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
    /** 底部浮层：'' 无 / 'at' 成员选择 / 'emoji' 表情。两者互斥（高度固定，叠着会顶飞消息区） */
    panel: '',
    atKeyword: '',
    atList: [],
    atLoading: false,
    /** 成员接口失败：不能显示成「没有找到这位校友」，那会让用户以为搜错了 */
    atError: false,
    /** 光标位置：插入表情/@ 时要插到光标处而不是末尾 */
    inputCursor: 0,
    /** 从面板选完人需要把焦点还给输入框；下次 blur 置回 false，否则属性值不变不再触发 */
    focusInput: false,
    emojiGroups: EMOJI_GROUPS,
    emojiIndex: 0,
    emojiList: EMOJI_GROUPS[0].list,
    /** 正在回复的消息 { id, name, text }；null = 普通发言 */
    replyTo: null,
    scrollInto: '',
    /** 消息区底部留白（键盘/面板弹出时撑高，保证最新消息不被悬浮输入栏盖住） */
    msgPadBottom: 0,
    /** 上滑看历史时显示「回到底部」浮层按钮 */
    showToBottom: false,
    /** 上滑期间新到的消息数：只做角标提示，不打断用户看历史（QQ 同款） */
    unread: 0,
    loading: true,
    /** 我是否已确认群规（确认后才允许发第一条） */
    needAgree: false
  },

  // ============ 生命周期 ============

  onLoad: function (opt) {
    // 群聊只有登录用户能用（发消息/撤回/已读全是 401，游客进来点了没反应）
    auth.ensureLogin().catch(function () {
      setTimeout(function () { mp.navigateBack() }, 600)
    })
    const self = this
    const info = typeof mp.getWindowInfo === 'function' ? mp.getWindowInfo() : mp.getSystemInfoSync()
    // 屏幕换算：px↔rpx，以及消息区可视高度（用于「回到底部」按钮显隐判定）
    this._px2rpx = 750 / (info.windowWidth || 375)
    this._scrollH = (info.windowHeight || 700) - 140
    // 图片尺寸缓存 / 在途标记：同一张图只量一次，滚动回来看历史不重复请求
    this._sizeCache = {}
    this._sizing = {}
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
      // 回到前台必须先「体检」：小程序切后台会被系统静默掐断 WS，
      // 但 onClose 常常不触发，state 还停在 open —— 不查的话页面永远收不到新消息
      ws.ensureAlive()
      // WS 没连上（或正在连）：立刻用轮询兜住，不能让用户干等
      if (!ws.isOpen()) {
        this.startPolling()
        this.pullSince()
      }
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
    this._unloaded = true
    // 搜索 @ 成员的防抖定时器也要清，否则页面销毁后还会 setData（会告警）
    if (this._atTimer) {
      clearTimeout(this._atTimer)
      this._atTimer = null
    }
    // 图片尺寸的合并定时器同理（E-09：销毁时必须清干净）
    if (this._sizeTimer) {
      clearTimeout(this._sizeTimer)
      this._sizeTimer = null
    }
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
      const msgs = self.relayoutAll(self.decorate(list, false))
      self.setData({
        msgs: msgs,
        loading: false,
        needAgree: !detail.rulesAgreed,
        showRules: !detail.rulesAgreed,
        scrollInto: msgs.length ? 'm-last' : ''
      })
      self.bindWs()
      ws.connect()
      // 双保险：进页面先把轮询挂上。WS 一旦 open 会自动 stopPolling，
      // 所以正常情况下用户感知不到它；但握手卡住 / 网关没配 /api/ws 时，
      // 这就是「还能收到消息」的唯一保证（以前只能等降级，要三十多秒）
      self.startPolling()
      self.reportRead()
      // 历史消息里的图片没有宽高，进页面量一轮（同 URL 有缓存）
      self.resolveImageSizes()
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
      // 只有「连接开着且没假死」才敢停轮询。
      // 以前只判 isOpen()：切后台被静默断掉的连接 state 还是 open，
      // 轮询一停就再也不会回来，成了「别人发消息我永远收不到」。
      if (ws.isOpen() && !ws.isStale()) {
        self.stopPolling()
        return
      }
      // 没连上 / 已假死：顺手把连接救一下，别让它一直躺平
      ws.ensureAlive()
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
        // 文本消息且带 @：解析成可点片段（@昵称→资料卡，@所有人→只高亮）
        segments: (m.type || 'text') === 'text' ? parseAt(m.content, m.atUserIds, m.atAll) : null,
        atAll: !!m.atAll,
        atText: m.atAll ? '所有人' : '',
        // 引用回复：服务端存快照，跳转用 replyToId（原消息不在列表里就只显示文字）
        replyToId: m.replyToId ? String(m.replyToId) : '',
        replyToName: m.replyToName || '',
        replyToText: m.replyToText || '',
        // 引用块里的时间：服务端快照只有 name/text，时间由 relayoutAll 按
        // replyToId 回查被引用消息补上（QQ 的引用卡片是「昵称 + 时间 + 展开箭头」）
        replyToTime: m.replyToTime || '',
        // 被引用的是一条图片：replyToText 只有「[图片]」四个字，引用卡里看不出是哪张。
        // replyToImg 由服务端按 replyToId 回查补全，拿不到时 relayoutAll 用本地列表兜底
        replyToImg: m.replyToImg || '',
        deleted: m.deleted || 0,
        // 1 本人撤回 / 2 管理员删除：文案不同，所以不能只看 deleted
        deletedBy: m.deletedBy || 0,
        recalled: Number(m.deletedBy) === 1,
        createdAt: m.createdAt || '',
        timeText: shortTime(m.createdAt),
        // 图片消息：imgW/imgH 由 getImageInfo 算（服务端只存 URL，不知道宽高）
        imgW: 0,
        imgH: 0,
        uploading: false,
        mine: mine,
        pending: false,
        failed: false
      }
    })
  },

  /**
   * 消息布局重排 —— 所有「msgs 变了」的地方都走这里，统一算两件事：
   *
   * <p>1. **时间分隔条**：与上一条间隔 ≥5 分钟（或第一条）才给 showTime，
   *    文案按「今天 HH:mm / 昨天 HH:mm / 前天 HH:mm / M月D日 HH:mm」分级。
   *    时间不再挂在每条气泡下面 —— QQ 就是这么做的，一屏里少十几个灰点，干净的。
   *
   * <p>2. **引用块时间 / 引用图**：服务端快照只存 replyToName / replyToText，没有时间，
   *    图片也只存成「[图片]」四个字。被引用的消息通常就在当前列表里，按 replyToId
   *    回查一下就能补齐（服务端已做同样的回查，这里只是兜底：老版本后端、
   *    刚发出的本地消息都还没有 replyToImg）。
   *
   * <p>本地乐观消息 createdAt 为空，parseTime 返回 0：既不显示分隔条，
   * 也不能污染 lastTs（否则下一条真实消息会算错间隔）。
   */
  relayoutAll: function (arr) {
    if (!arr || !arr.length) return arr || []
    const byId = {}
    arr.forEach(function (m) {
      if (m && m.id) byId[String(m.id)] = m
    })

    let lastTs = 0
    return arr.map(function (m) {
      const ts = parseTime(m.createdAt)
      let showTime = false
      // ts 为 0（本地发送中 / 时间串异常）时既不出分隔条，也不动 lastTs，
      // 否则后续真实消息的间隔会被算成「跟 0 比」，全都显示分隔条
      if (ts) {
        if (!lastTs || ts - lastTs >= TIME_GAP_MS) showTime = true
        lastTs = ts
      }

      const patch = {
        showTime: showTime,
        timeLabel: showTime ? fullTime(m.createdAt) : ''
      }
      if (m.replyToId) {
        const src = byId[m.replyToId]
        if (src) {
          if (!m.replyToTime) patch.replyToTime = shortTime(src.createdAt)
          // 引用图兜底：服务端没给（老接口 / 本地乐观消息）就在本地列表里找原图。
          // 撤回的图不回显 —— 撤回就该当没发过，缩略图也不能留
          if (!m.replyToImg && src.type === 'image' && !src.deleted) {
            patch.replyToImg = src.content
          }
        }
      }
      return Object.assign({}, m, patch)
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
          const local = list[i]
          // 兜底：服务端快照偶发为空（旧包未部署引用逻辑 / 瞬时抖动），
          // 本地乐观消息已带引用信息，直接沿用，避免回显后引用块凭空消失。
          if (!incoming.replyToName && local.replyToName) {
            incoming.replyToId = incoming.replyToId || local.replyToId
            incoming.replyToName = local.replyToName
            incoming.replyToText = incoming.replyToText || local.replyToText
          }
          // 回显的 VO 不带尺寸（服务端只存 URL）：图片气泡已经按本地量的尺寸
          // 占好位了，这里丢掉会先缩成一个兜底方块再跳回来，必须沿用
          if (local.imgW) {
            incoming.imgW = local.imgW
            incoming.imgH = local.imgH
          }
          list[i] = incoming
          list = this.relayoutAll(list)
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
    // 追加的是别人的消息、且用户正在看历史 → 角标 +1（自己的消息不提示）
    if (!incoming.mine) this.bumpUnread(1)
    list = this.relayoutAll(list)
    const patch = { msgs: list.slice(-200) }
    // 用户正上滑看历史时不要把他拽回底部（QQ / 微信都不拽），只加角标
    if (!this.data.showToBottom) patch.scrollInto = 'm-last'
    this.setData(patch)
    this.reportRead()
    // 别人实时发来的图片：我们不知道宽高，量一下再按比例排版
    if (incoming.type === 'image') this.resolveImageSizes()
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
      // 补拉回来的别人消息也要计未读（重连补拉常常一次好几条）
      const others = fresh.filter(function (m) { return !m.mine }).length
      if (others) self.bumpUnread(others)
      const patch = { msgs: self.relayoutAll(merged.slice(-200)) }
      if (!self.data.showToBottom) patch.scrollInto = 'm-last'
      self.setData(patch)
      self.reportRead()
      // 补拉可能带回别人的图片，尺寸同样要补
      self.resolveImageSizes()
    }).catch(function () {})
  },

  /**
   * 输入框内容变化。
   *
   * <p>除了存文本，还要做两件事：
   * 1. 记住光标位置 —— 表情/@ 要插在光标处，插在末尾会被用户骂「我明明点在中间」。
   *    位置只用来算「文本从哪切」，<b>不要</b>绑到 input 的 cursor 属性上：
   *    那个属性会在聚焦时强行把光标挪走，用户点输入框想接着打字却跳到开头。
   * 2. 用户手打「@」就地把成员面板调出来（而不是只能去点左边的 @ 按钮）。
   *    只在「本次刚好新增 1 个字符且是 @」时触发，粘贴一整段带 @ 的文本不会误弹。
   */
  onInput: function (e) {
    const v = e.detail.value || ''
    const cursor = typeof e.detail.cursor === 'number' ? e.detail.cursor : v.length
    const prev = this.data.inputText || ''
    const patch = { inputText: v, inputCursor: cursor }

    const typedAt = v.length === prev.length + 1 && cursor > 0 && v.charAt(cursor - 1) === '@'
    if (typedAt && this.data.panel !== 'at') {
      // 必须先把「@」落到 data 再开面板：openAtPanel 会 setData，
      // data 里没有这个 @ 的话 input 的 value 会被回写成旧值，@ 白打了
      this.setData(patch)
      // 记住这个 @ 的位置，选完人要把 @ 替换成「@昵称 」
      this._atAnchor = cursor - 1
      this.openAtPanel()
      return
    }
    this.setData(patch)
  },

  /** 聚焦输入框 = 用户要打字，表情/@ 面板必须让位（否则键盘和面板互相顶） */
  onFocus: function () {
    if (this.data.panel) this.setData({ panel: '' })
  },

  /** blur 后把 focus 标记归位，下次 pickAt 才能再次把焦点推回输入框 */
  onBlur: function () {
    if (this.data.focusInput) this.setData({ focusInput: false })
  },

  onKeyboard: function (e) {
    const h = (e.detail && e.detail.height) || 0
    const patch = { keyboardHeight: h }
    // 键盘弹起：消息区底部留白撑到「键盘高度 + 余量」，最新消息才不会被悬浮输入栏盖住
    patch.msgPadBottom = h > 0 ? Math.round(h * (this._px2rpx || 2)) + 24 : 0
    // 键盘起来了要打字：表情面板必须收（它没有输入需求，叠着只会把消息区压没）。
    // @ 面板反而允许键盘共存 —— 面板顶部就是成员搜索框，点它必然弹键盘。
    if (h > 0 && this.data.panel === 'emoji') patch.panel = ''
    this.setData(patch)
  },

  /** 消息区滚动：判断是否离开底部，决定是否显示「回到底部」浮层 */
  onScroll: function (e) {
    const d = e.detail || {}
    const sh = this._scrollH || 0
    const atBottom = (d.scrollTop || 0) + sh >= (d.scrollHeight || 0) - 40
    const show = !atBottom && (this.data.msgs || []).length > 0
    const patch = {}
    if (show !== this.data.showToBottom) patch.showToBottom = show
    // 滚回底部 = 已读，角标必须清掉（否则用户看着最新消息还挂着红点）
    if (atBottom && this.data.unread) patch.unread = 0
    if (patch.showToBottom !== undefined || patch.unread !== undefined) this.setData(patch)
  },

  /**
   * 累加未读角标。
   *
   * <p>只在「用户正上滑看历史」时计数：停在底部看新消息是正常浏览，
   * 给它戴个红点纯属噪音。计数本身不做截断，展示时再按 99+ 处理。
   */
  bumpUnread: function (n) {
    if (!this.data.showToBottom) return
    this.setData({ unread: (this.data.unread || 0) + (n || 1) })
  },

  /** 点浮层按钮：滚到最新消息 */
  scrollToBottom: function () {
    const self = this
    this.setData({ scrollInto: '', showToBottom: false, unread: 0 })
    setTimeout(function () {
      if (!self._unloaded) self.setData({ scrollInto: 'm-last' })
    }, 30)
  },

  onMicTap: function () {
    toast('语音功能开发中')
  },

  /**
   * 「＋」→ 拍照 / 相册。
   *
   * <p>没有做成「点一下直接弹相册」：群聊里发图有两种真实场景 ——
   * 拍一张现成的（拍黑板、拍快递柜取件码）和从相册挑一张，
   * 多一次选择比让用户先退出相册再找相机快。
   */
  onPlusTap: function () {
    const self = this
    // 先拦不能发言的状态，别等用户挑完图才告诉他发不出去
    if (!this.data.canSend) {
      toast(this.data.blockReason || '当前无法发言')
      return
    }
    if (this.data.needAgree) {
      this.setData({ showRules: true })
      return
    }
    if (typeof mp.showActionSheet !== 'function') return
    mp.showActionSheet({
      itemList: ['拍照', '从相册选择'],
      success: function (res) {
        self.pickImage(res.tapIndex === 0 ? 'camera' : 'album')
      },
      fail: function () { /* 用户取消 */ }
    })
  },

  /**
   * 选一张图。
   *
   * <p><b>count 只能是 1</b>：群发言有间隔频控（≤500 人的群 10 秒 1 条），
   * 一次多选 9 张，第 2 张必然 429「发言太频繁」。与其让用户挑完 9 张再被打回，
   * 不如一张一张发。真要支持多图，得后端把同一 clientMsgId 批次的频控合并计算。
   *
   * <p>基础库 <2.10.0 没有 chooseMedia，退回老的 chooseImage。
   */
  pickImage: function (source) {
    const self = this
    const onPicked = function (path, size) {
      if (path) self.sendImage(path, size)
    }
    const onFail = function (err) {
      const msg = (err && err.errMsg) || ''
      if (msg.indexOf('cancel') >= 0) return
      toast('打不开相机/相册，检查一下权限吧')
    }

    if (typeof mp.chooseMedia === 'function') {
      mp.chooseMedia({
        count: 1,
        mediaType: ['image'],
        sourceType: [source],
        sizeType: ['compressed'],
        success: function (res) {
          const f = res.tempFiles && res.tempFiles[0]
          if (f && f.tempFilePath) onPicked(f.tempFilePath, f.size)
        },
        fail: onFail
      })
      return
    }
    mp.chooseImage({
      count: 1,
      sourceType: [source],
      sizeType: ['compressed'],
      success: function (res) {
        const f = (res.tempFiles && res.tempFiles[0]) || null
        const path = f ? f.path : (res.tempFilePaths && res.tempFilePaths[0])
        onPicked(path, f ? f.size : 0)
      },
      fail: onFail
    })
  },

  /**
   * 发一张图：先上本地占位气泡（立刻有反馈），上传成功再发消息。
   *
   * <p>顺序很重要 —— 先插入气泡再上传，用户在弱网下也能看到「上传中…」，
   * 而不是点了没反应。上传失败只把这一个气泡标红，可以点着重试。
   */
  sendImage: function (path, size) {
    const self = this
    if (this.data.needAgree) {
      this.setData({ showRules: true })
      return
    }
    if (!this.data.canSend) {
      toast(this.data.blockReason || '当前无法发言')
      return
    }
    if (size && size > 20 * 1024 * 1024) {
      toast('这张图太大了，换一张吧')
      return
    }

    const clientMsgId = genId()
    let reply = this.data.replyTo
    // 引用正在发送中的本地消息没有意义（服务端取不到快照）
    if (reply && String(reply.id).indexOf('local-') === 0) reply = null

    // 图片消息不携带 @：@ 是文本里的锚点，图片里没有地方显示它，
    // 带上只会给对方一个「被 @ 了但找不到在哪」的提醒。
    const local = {
      id: 'local-' + clientMsgId,
      clientMsgId: clientMsgId,
      senderId: this.data.myId,
      senderName: '我',
      senderAvatar: this.data.myAvatar || '',
      content: path,
      type: 'image',
      segments: null,
      atAll: false,
      atText: '',
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
      failed: false,
      uploading: true,
      imgW: 0,
      imgH: 0
    }

    this.setData({
      msgs: this.relayoutAll(this.data.msgs.concat([local])),
      replyTo: null,
      panel: '',
      scrollInto: 'm-last'
    })
    // 本地就能量出尺寸，气泡按原图比例先占好位，上传回来不用再跳一下
    this.measureLocalImage(clientMsgId, path)
    this.doUploadAndSend(clientMsgId, path, reply)
  },

  /** 上传 + 发送。retryImage 也复用这条路（clientMsgId 不变，服务端幂等） */
  doUploadAndSend: function (clientMsgId, path, reply) {
    const self = this
    upload.uploadImage(path).then(function (url) {
      if (self._unloaded) return
      self.patchLocal(clientMsgId, { content: url, uploading: false })
      const payload = {
        msgType: 'image',
        content: url,
        clientMsgId: clientMsgId,
        atUserIds: [],
        atAll: false,
        replyToId: reply ? reply.id : ''
      }
      const sent = ws.sendMsg(self.data.groupId, payload)
      if (!sent) self.sendByHttp(payload)
    }).catch(function () {
      if (self._unloaded) return
      // upload.js 内部已经按具体原因 toast 过了（超 5MB / 网络 / 登录失效），
      // 这里不再叠一层，气泡上的「发送失败，点一下重试」就是引导
      self.markLocalFailed(clientMsgId)
    })
  },

  /** 发送失败的图片：点一下重传重发（clientMsgId 不变 → 服务端幂等，不会发两条） */
  retryImage: function (m) {
    if (!m || !m.clientMsgId) return
    const path = m.content
    // content 已经是服务端地址 = 图传上去了、只是消息没发出去，直接重发消息
    const ready = upload.isServerUrl(path)
    this.patchLocal(m.clientMsgId, { pending: true, failed: false, uploading: !ready })
    if (ready) {
      const payload = {
        msgType: 'image',
        content: path,
        clientMsgId: m.clientMsgId,
        atUserIds: [],
        atAll: false,
        replyToId: m.replyToId || ''
      }
      if (!ws.sendMsg(this.data.groupId, payload)) this.sendByHttp(payload)
      return
    }
    this.patchLocal(m.clientMsgId, { content: path })
    this.doUploadAndSend(m.clientMsgId, path, {
      id: m.replyToId || '',
      name: m.replyToName || '',
      text: m.replyToText || ''
    })
  },

  /** 本地图片量尺寸（getImageInfo 对 tempFilePath 同样有效） */
  measureLocalImage: function (clientMsgId, path) {
    const self = this
    if (typeof mp.getImageInfo !== 'function') return
    mp.getImageInfo({
      src: path,
      success: function (res) {
        if (self._unloaded) return
        const box = fitImageBox(res.width, res.height)
        self.patchLocal(clientMsgId, { imgW: box.w, imgH: box.h })
      },
      fail: function () { /* 量不出就用兜底尺寸，不影响发送 */ }
    })
  },

  /**
   * 补算图片尺寸（历史消息 / 别人的实时消息）。
   *
   * <p>服务端只存 URL，不知道宽高。不知道宽高就只能给死尺寸，
   * 长截图会被裁得只剩中间一条。所以进页面后按 URL 量一次，
   * 同 URL 走内存缓存（_sizeCache），异步结果攒 80ms 合并成一次 setData。
   */
  resolveImageSizes: function () {
    const self = this
    if (typeof mp.getImageInfo !== 'function') return
    this._sizeCache = this._sizeCache || {}

    ;(this.data.msgs || []).forEach(function (m) {
      if (m.type !== 'image' || m.imgW || m.uploading || m.deleted) return
      const url = m.content
      if (!url || url.indexOf('http') !== 0) return
      if (self._sizeCache[url]) {
        self.queueImageSize(url, self._sizeCache[url])
        return
      }
      if (self._sizing[url]) return
      self._sizing[url] = true
      mp.getImageInfo({
        src: url,
        success: function (res) {
          const box = fitImageBox(res.width, res.height)
          self._sizeCache[url] = box
          delete self._sizing[url]
          if (!self._unloaded) self.queueImageSize(url, box)
        },
        fail: function () { delete self._sizing[url] }
      })
    })
  },

  /** 攒批量：一张图一次 setData 太浪费（E-02） */
  queueImageSize: function (url, box) {
    const self = this
    this._sizeQ = this._sizeQ || {}
    this._sizeQ[url] = box
    if (this._sizeTimer) return
    this._sizeTimer = setTimeout(function () {
      self._sizeTimer = null
      const q = self._sizeQ || {}
      self._sizeQ = {}
      if (self._unloaded || !Object.keys(q).length) return
      const msgs = (self.data.msgs || []).map(function (m) {
        if (m.type !== 'image' || !q[m.content]) return m
        return Object.assign({}, m, { imgW: q[m.content].w, imgH: q[m.content].h })
      })
      self.setData({ msgs: msgs })
    }, 80)
  },

  /** 按 clientMsgId 改一条本地消息（乐观更新专用） */
  patchLocal: function (clientMsgId, patch) {
    let hit = false
    const msgs = (this.data.msgs || []).map(function (m) {
      if (!m.clientMsgId || m.clientMsgId !== clientMsgId) return m
      hit = true
      return Object.assign({}, m, patch)
    })
    if (hit) this.setData({ msgs: msgs })
  },

  /** 点图片：正常 → 全屏预览（可左右翻群里所有图）；失败 → 重试 */
  onTapImage: function (e) {
    const idx = Number(e.currentTarget.dataset.idx)
    const m = (this.data.msgs || [])[idx]
    if (!m) return
    if (m.failed) {
      this.retryImage(m)
      return
    }
    if (m.uploading) return
    const urls = (this.data.msgs || []).filter(function (x) {
      return x.type === 'image' && !x.deleted && !x.uploading && x.content
    }).map(function (x) { return x.content })
    if (!urls.length || typeof mp.previewImage !== 'function') return
    mp.previewImage({ current: m.content, urls: urls })
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
    let reply = this.data.replyTo
    // 兜底：万一引用了一跳还没上服务器的本地消息（id 形如 local-xxxx），
    // 服务端取不到快照会让引用块回显后消失 —— 直接当作普通发言发出去。
    if (reply && String(reply.id).indexOf('local-') === 0) reply = null
    // ★ 必须先把 atUserIds 取出来再清 atUsers：
    //   之前是先 setData({atUsers: []}) 再 map，取到的永远是空数组，
    //   表现就是「@ 了人但对方收不到提醒」（t_group_mention 也不落库）。
    const atUserIds = (this.data.atUsers || []).map(function (u) { return String(u.id) })
    const local = {
      id: 'local-' + clientMsgId,
      clientMsgId: clientMsgId,
      senderId: this.data.myId,
      senderName: '我',
      senderAvatar: this.data.myAvatar || '',
      content: text,
      type: 'text',
      segments: parseAt(text, atUserIds, this.data.atAll),
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
      // 走一次重排：本地消息也要能显示引用块时间（被引用那条就在列表里）
      msgs: this.relayoutAll(this.data.msgs.concat([local])),
      inputText: '',
      inputCursor: 0,
      atUsers: [],
      atAll: false,
      replyTo: null,
      panel: '',
      scrollInto: 'm-last'
    })

    const payload = {
      msgType: 'text',
      content: text,
      clientMsgId: clientMsgId,
      atUserIds: atUserIds,
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

  /**
   * 把「还在发」的消息标成失败。
   *
   * @param clientMsgId 传了就只标这一条（图片单独上传失败时用）；
   *                    不传标全部 pending（WS 报错时连带的保守处理）。
   */
  markLocalFailed: function (clientMsgId) {
    const msgs = this.data.msgs.map(function (m) {
      if (!m.pending) return m
      if (clientMsgId && m.clientMsgId !== clientMsgId) return m
      return Object.assign({}, m, {
        pending: false, failed: true, uploading: false, timeText: '发送失败'
      })
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
    // 本地乐观消息（id 形如 local-xxxx）还在「发送中」、没上服务器，
    // 引用它毫无意义：服务端解析不出真实消息 id，取不到快照，
    // 回显后引用块会凭空消失。所以本地消息不给「回复」入口。
    const isLocal = m.id && String(m.id).indexOf('local-') === 0
    if (!m.deleted && !isLocal) actions.push({ key: 'reply', label: '回复' })
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
    this.setData({ msgs: this.relayoutAll(msgs) })
  },

  /** 引用某条消息：只记 id + 展示用文案，真正发出去时服务端重新取快照 */
  replyToMsg: function (m) {
    // 本地乐观消息（id 形如 local-xxxx）还在发送中、没上服务器，不能引用：
    // 服务端解析不出真实消息 id，取不到快照，回显后引用块会消失。
    if (!m || !m.id || String(m.id).indexOf('local-') === 0) {
      toast('消息还在发送中，稍后会自动刷新，请过两秒再回复')
      return
    }
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

  // ============ @ 面板（底部浮层，不跳页） ============

  /**
   * 打开成员面板。
   *
   * <p>只有「手打 @」一条入口（输入栏不再放 @ 按钮，避免和表情按钮挤在一起、
   * 也避免误点）；长按头像走 addAt，不经过这里。
   *
   * <p>面板和键盘互斥：同时存在会把消息区压成一条缝，
   * 所以打开前先收键盘，键盘高度归零后 .bottom-dock 自然落到底部。
   */
  openAtPanel: function () {
    const self = this
    hideKeyboard()
    this.setData({
      panel: 'at',
      atKeyword: '',
      atList: [],
      atLoading: true,
      atError: false,
      keyboardHeight: 0,
      focusInput: false,
      msgPadBottom: 610
    })
    http.get('/groups/' + this.data.groupId + '/members',
      { page: 1, size: 50 }, { silent: true }
    ).then(function (res) {
      self.setData({ atList: (res && res.records) || [], atLoading: false })
    }).catch(function () {
      self.setData({ atLoading: false, atError: true })
    })
  },

  /** 面板内搜索成员（防抖 300ms，一万人群里每敲一个字就请求太浪费） */
  onAtKeyword: function (e) {
    const kw = e.detail.value || ''
    this.setData({ atKeyword: kw })
    if (this._atTimer) clearTimeout(this._atTimer)
    const self = this
    this._atTimer = setTimeout(function () { self.searchAt(kw) }, 300)
  },

  searchAt: function (kw) {
    const self = this
    this.setData({ atLoading: true, atError: false })
    http.get('/groups/' + this.data.groupId + '/members',
      { keyword: kw, page: 1, size: 50 }, { silent: true }
    ).then(function (res) {
      // 回来时关键词可能已经被改掉了：只认最后一次请求的结果
      if (self.data.atKeyword !== kw) return
      self.setData({ atList: (res && res.records) || [], atLoading: false })
    }).catch(function () {
      if (self.data.atKeyword !== kw) return
      self.setData({ atLoading: false, atError: true })
    })
  },

  /** 点消息区空白处收起面板（和微信一致，不用非得去点「取消」） */
  onTapMsgs: function () {
    if (this.data.panel) this.closePanel()
  },

  closePanel: function () {
    if (this._atTimer) {
      clearTimeout(this._atTimer)
      this._atTimer = null
    }
    if (!this.data.panel) return
    const self = this
    this.setData({ panel: '', scrollInto: '', msgPadBottom: 0 })
    // 面板收掉后消息区变高，最后一条会被压在底部之外，重新滚一次
    setTimeout(function () {
      // 60ms 内用户可能已经退出群聊页，此时 setData 会告警
      if (self._unloaded) return
      self.setData({ scrollInto: 'm-last' })
    }, 60)
  },

  /** 面板里选人 */
  pickAt: function (e) {
    const idx = Number(e.currentTarget.dataset.idx)
    const u = (this.data.atList || [])[idx]
    if (!u) return
    if (String(u.userId) === this.data.myId) {
      toast('不能 @ 自己')
      return
    }
    const ok = this.insertAt({ id: u.userId, nickname: u.nickname || '校友' })
    if (!ok) return
    this.closePanel()
    // 焦点还给输入框：用户选完人接着打字是常态
    this.setData({ focusInput: true })
  },

  /**
   * 把一个成员插入 @ 列表与输入框。
   *
   * <p>两条入口共用：面板选人 / 长按头像（长按头像是最快的方式，
   * 不用先去列表里找人）。
   */
  insertAt: function (user) {
    if (!user || !user.id) return false
    const name = user.nickname || '校友'
    const list = (this.data.atUsers || []).slice()
    if (list.some(function (u) { return String(u.id) === String(user.id) })) {
      toast('已经 @ 过 ' + name + ' 了')
      return false
    }
    if (list.length >= MAX_AT) {
      toast('单条消息最多 @ ' + MAX_AT + ' 人')
      return false
    }
    list.push({ id: String(user.id), nickname: name })

    const text = this.data.inputText || ''
    const anchor = typeof this._atAnchor === 'number' ? this._atAnchor : -1
    let next
    let caret
    if (anchor >= 0 && anchor < text.length && text.charAt(anchor) === '@') {
      // 手打 @ 触发：把这个 @ 换成「@昵称 」，否则文本里会留下两个 @
      next = text.slice(0, anchor) + '@' + name + ' ' + text.slice(anchor + 1)
      caret = anchor + name.length + 2
    } else {
      const pos = this.data.inputCursor
      const at = (typeof pos === 'number' && pos >= 0 && pos <= text.length) ? pos : text.length
      next = text.slice(0, at) + '@' + name + ' ' + text.slice(at)
      caret = at + name.length + 2
    }
    this._atAnchor = -1
    this.setData({ atUsers: list, inputText: next, inputCursor: caret })
    return true
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

  // ============ 表情面板 ============

  toggleEmojiPanel: function () {
    if (this.data.panel === 'emoji') {
      this.closePanel()
      return
    }
    hideKeyboard()
    const self = this
    this.setData({
      panel: 'emoji',
      keyboardHeight: 0,
      focusInput: false,
      scrollInto: '',
      msgPadBottom: 520
    })
    setTimeout(function () {
      if (self._unloaded) return
      self.setData({ scrollInto: 'm-last' })
    }, 60)
  },

  switchEmoji: function (e) {
    const i = Number(e.currentTarget.dataset.idx) || 0
    const g = this.data.emojiGroups[i] || this.data.emojiGroups[0]
    this.setData({ emojiIndex: i, emojiList: g.list })
  },

  /** 插入表情到光标处（不是末尾：用户把光标点到句中就是要插在句中） */
  addEmoji: function (e) {
    const emoji = e.currentTarget.dataset.emoji
    if (!emoji) return
    const text = this.data.inputText || ''
    let pos = this.data.inputCursor
    if (typeof pos !== 'number' || pos < 0 || pos > text.length) pos = text.length
    const next = text.slice(0, pos) + emoji + text.slice(pos)
    this.setData({ inputText: next, inputCursor: pos + emoji.length })
  },

  /**
   * 面板里的退格。
   *
   * <p>表情面板打开时键盘是收起的，没有系统删除键，
   * 必须自己给一个，否则用户打错字只能先切回键盘。
   */
  emojiBackspace: function () {
    const text = this.data.inputText || ''
    if (!text) return
    const pos = this.data.inputCursor
    if (typeof pos !== 'number' || pos <= 0 || pos > text.length) return
    // emoji 是代理对（2 个 code unit）：只删 1 个会留下半截乱码
    const code = text.charCodeAt(pos - 1)
    const lastLen = (code >= 0xDC00 && code <= 0xDFFF && pos >= 2) ? 2 : 1
    const next = text.slice(0, pos - lastLen) + text.slice(pos)
    this.setData({ inputText: next, inputCursor: pos - lastLen })
  },

  /**
   * 加进 @ 列表（长按头像走这里）。
   *
   * <p>这条路径不经过输入框，data 里的光标位置不可信，
   * 所以直接追加到文本末尾，避免插到句中把句子切开。
   */
  addAt: function (user) {
    this._atAnchor = -1
    this.setData({ inputCursor: (this.data.inputText || '').length })
    if (this.insertAt(user)) toast('已 @ ' + ((user && user.nickname) || '校友'))
  },

  /** 点消息里的头像 → 资料卡 / 私聊 */
  onAvatar: function (e) {
    const uid = e.currentTarget.dataset.uid
    if (!uid || String(uid) === this.data.myId) return
    mp.navigateTo({ url: '/pages/user/space/space?id=' + uid })
  },

  /** 点气泡里高亮的 @昵称 → 对方资料卡（和点头像去的是同一页） */
  onTapAt: function (e) {
    const uid = e.currentTarget.dataset.uid
    if (!uid) return
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

/**
 * 时间分隔条文案（居中那条）。
 *
 * <p>按「离今天几天」分级，而不是简单拼日期：聊天场景里「昨天」「前天」
 * 比「10-26」更容易一眼定位。跨年的消息才带年份。
 */
function fullTime(t) {
  if (!t) return ''
  const d = new Date(String(t).replace(/-/g, '/'))
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const hm = pad2(d.getHours()) + ':' + pad2(d.getMinutes())
  const today0 = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const that0 = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const diffDay = Math.round((today0 - that0) / 86400000)
  if (diffDay === 0) return hm
  if (diffDay === 1) return '昨天 ' + hm
  if (diffDay === 2) return '前天 ' + hm
  if (d.getFullYear() === now.getFullYear()) {
    return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + hm
  }
  return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + hm
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

/**
 * 图片气泡尺寸（rpx）：按原图比例缩放到「限宽 + 限高」的框里。
 *
 * <p>为什么不用 {@code mode="widthFix"}：那玩意儿的宽度就是气泡宽度、
 * 高度 = 比例 × 宽度，长截图（比例 1:5）会把整个消息流撑穿，一屏只能看半条。
 * 全站单图口径统一是「算出尺寸 + aspectFill」，看全图走 previewImage。
 */
function fitImageBox(w, h) {
  const MAX_W = 360
  const MAX_H = 420
  const MIN_W = 140
  if (!w || !h) return { w: 300, h: 225 }
  const r = w / h
  let dw = MAX_W
  let dh = Math.round(MAX_W / r)
  if (dh > MAX_H) {
    dh = MAX_H
    dw = Math.round(MAX_H * r)
  }
  // 极窄的图（长截图）：宽度会被算成一根牙签，抬到最小宽度，高度继续限死
  if (dw < MIN_W) {
    dw = MIN_W
    dh = Math.min(MAX_H, Math.round(MIN_W / r))
  }
  return { w: dw, h: dh }
}

/** 客户端幂等 id（不依赖 crypto，低版本基础库也能跑） */
function genId() {
  return 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
}

/**
 * 把消息正文里的 @昵称 解析成「可点片段」。
 *
 * <p>后端存的是两段信息：content（纯文本，形如「xx @张三 @李四 收到」）
 * 和 atUserIds（被 @ 的成员 id，与 content 里 @ 的出现顺序一一对应）。
 * 这里按下标对应：第 i 个 @昵称 映射到 atUserIds[i]，所以不用把昵称回传做匹配，
 * 对方改昵称也不影响跳转。
 *
 * <p>@所有人 单独处理：atAll=true 时任意位置的「@所有人」都只高亮不跳转
 * （它不是某个人，点了没地方去）。其余 @昵称 按 atUserIds 顺序分配 uid。
 * 若 @ 数量多于 atUserIds（用户自己手打了一个 @xxx），多出来的按普通文本渲染。
 */
function parseAt(content, atUserIds, atAll) {
  if (!content) return null
  if (content.indexOf('@') === -1) return null
  const ids = (atUserIds && atUserIds.length)
    ? atUserIds.map(function (x) { return String(x) })
    : []
  const segs = []
  let atIdx = 0
  const parts = content.split('@')
  // parts[0] 是第一个 @ 之前的正文
  if (parts[0]) segs.push({ t: 'text', v: parts[0], k: segs.length })
  for (let i = 1; i < parts.length; i++) {
    const p = parts[i]
    if (!p) { // 结尾的 @ 或连续的 @@
      segs.push({ t: 'text', v: '@', k: segs.length })
      continue
    }
    const m = p.match(/^(\S+)([\s\S]*)$/)
    const name = m ? m[1] : p
    const rest = m ? m[2] : ''
    if (atAll && name === '所有人') {
      segs.push({ t: 'atall', v: '@' + name, k: segs.length })
    } else if (atIdx < ids.length) {
      segs.push({ t: 'at', v: '@' + name, uid: ids[atIdx], k: segs.length })
      atIdx++
    } else {
      // @ 数量多于 atUserIds（用户自己手打 @xxx）：当普通文本，不误点
      segs.push({ t: 'text', v: '@' + name, k: segs.length })
    }
    if (rest) segs.push({ t: 'text', v: rest, k: segs.length })
  }
  return segs
}

/**
 * 收起键盘。
 *
 * <p>表情 / @ 面板和键盘是互斥的：不主动收键盘，面板会被顶到键盘上方，
 * 只剩半截还能看见。低版本没有 hideKeyboard，靠 input 失焦兜底（不抛错即可）。
 */
function hideKeyboard() {
  if (typeof mp.hideKeyboard !== 'function') return
  try {
    mp.hideKeyboard({ fail: function () {} })
  } catch (e) {
    /* 没键盘时部分机型直接抛错，忽略 */
  }
}
