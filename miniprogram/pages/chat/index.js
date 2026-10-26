const http = require('../../utils/request')
const upload = require('../../utils/upload')
const { mp, timeAgo } = require('../../utils/util')
const { resolve } = require('../../utils/img')

Page({
  data: {
    convoId: '',
    peerId: '',
    peerName: '同学',
    peerAvatar: '',
    peerVerified: false,
    peerTags: [],
    anon: false,
    anonName: '',
    myAvatar: '',
    showCta: false,
    dayText: '今天',
    goodsCard: null,
    messages: [],
    draft: '',
    sending: false,
    scrollInto: ''
  },

  onLoad: function (options) {
    mp.setNavigationBarTitle({ title: '私信' })
    const cached = mp.getStorageSync('user') || {}
    this.setData({
      convoId: options.id || '',
      peerId: options.peer || '',
      goodsId: options.gid || '',
      goodsPrice: options.gp ? decodeURIComponent(options.gp) : '',
      goodsEmoji: options.ge ? decodeURIComponent(options.ge) : '',
      goodsStatus: options.gs ? decodeURIComponent(options.gs) : '',
      peerName: options.name ? decodeURIComponent(options.name) : '同学',
      anon: options.anon === '1',
      anonName: options.anonName ? decodeURIComponent(options.anonName) : '',
      myAvatar: resolve(cached.avatar),
      goodsCard: options.goods
        ? {
            title: decodeURIComponent(options.goods),
            price: options.gp ? decodeURIComponent(options.gp) : '',
            emoji: options.ge ? decodeURIComponent(options.ge) : '🛍️',
            statusText: '在售中'
          }
        : null
    })
    this.load()
  },

  /** 页面可见期间每 4s 轮询一次会话，让对方的新消息自动到达 */
  onShow: function () {
    const self = this
    if (this._pollTimer) return
    this._pollTimer = setInterval(function () {
      self.fetchConvoSilently()
    }, 4000)
  },

  onHide: function () {
    this.stopPoll()
  },

  onUnload: function () {
    this.stopPoll()
  },

  stopPoll: function () {
    if (this._pollTimer) {
      clearInterval(this._pollTimer)
      this._pollTimer = null
    }
  },

  load: function () {
    const self = this
    if (this.data.convoId) {
      this.fetchConvo()
    } else if (this.data.peerId) {
      // 建会话失败时的兜底：进页面后先补建会话
      this.startConvo(function () {
        self.fetchConvo()
      })
    } else {
      // 纯展示会话（无会话 id、无对端用户）
      const goods = this.data.goodsCard
      this.setData({
        showCta: !!goods,
        messages: [
          {
            id: 1,
            from: 'me',
            type: 'text',
            content: '你好！看到你发布的「' + (goods ? goods.title : '宝贝') + '」，还在吗？',
            timeText: nowTime()
          }
        ]
      })
    }
  },

  /** 拉取会话详情（含聊天记录） */
  fetchConvo: function () {
    const self = this
    http.get('/messages/' + this.data.convoId, null, { silent: true }).then(function (res) {
      self.applyConvo(res)
    }).catch(function () {
      mp.showToast({ title: '会话加载失败', icon: 'none' })
    })
  },

  /** 轮询专用：静默拉取，失败不打扰；发送中 / 上一次未返回时跳过 */
  fetchConvoSilently: function () {
    const self = this
    if (this._fetching || this.data.sending || !this.data.convoId) return
    this._fetching = true
    http.get('/messages/' + this.data.convoId, null, { silent: true }).then(function (res) {
      self._fetching = false
      self.applyConvo(res)
    }).catch(function () {
      self._fetching = false
    })
  },

  /** 发起（或复用）会话，成功后把会话 id 记下来 */
  startConvo: function (done) {
    const self = this
    if (!this.data.peerId) {
      if (done) done()
      return
    }
    http.post('/messages/start', {
      peerId: this.data.peerId,
      goodsId: this.data.goodsId || undefined,
      goodsTitle: this.data.goodsCard ? this.data.goodsCard.title : '',
      goodsPrice: this.data.goodsPrice,
      goodsEmoji: this.data.goodsEmoji,
      goodsStatus: this.data.goodsStatus === '' ? undefined : Number(this.data.goodsStatus),
      anon: this.data.anon ? 1 : undefined,
      anonName: this.data.anonName || undefined
    }, { silent: true }).then(function (convo) {
      self.setData({
        convoId: convo.id,
        peerName: convo.peerName || self.data.peerName,
        goodsCard: convo.goodsCard || self.data.goodsCard
      })
      self.applyPeer(convo)
      if (done) done()
    }).catch(function () {
      mp.showToast({ title: '会话创建失败', icon: 'none' })
    })
  },

  applyConvo: function (res) {
    const msgs = (res.messages || []).map(function (m) {
      m.timeText = shortTime(m.createdAt)
      m.id = String(m.id)
      return m
    })
    // 只在最后一条消息变化（对方新消息 / 首次加载）时更新列表并滚动，
    // 轮询无新消息时不动 messages，避免把正在翻历史的用户拽回底部
    const lastId = msgs.length ? msgs[msgs.length - 1].id : ''
    const changed = lastId !== this._lastMsgId
    this._lastMsgId = lastId
    const patch = {
      peerName: res.peerName || this.data.peerName,
      goodsCard: res.goodsCard || this.data.goodsCard
    }
    if (changed) {
      patch.messages = msgs
      patch.dayText = dayDivider(msgs)
      patch.scrollInto = ''
    }
    this.setData(patch)
    this.applyPeer(res)
    if (changed) this.scrollToEnd()
  },

  /** 用后端返回的对端信息刷新头部（头像 / 标签 / 认证 / 匿名） */
  applyPeer: function (res) {
    const tags = res.peerTags || []
    this.setData({
      peerAvatar: res.peerAvatar || '',
      peerVerified: !!res.peerVerified,
      peerTags: tags,
      anon: !!res.anon,
      // 只有对端是卖家时才展示「发起预订 / 小刀还价」
      showCta: tags.some(function (t) { return t.text === '卖家' })
    })
  },

  scrollToEnd: function () {
    const last = this.data.messages[this.data.messages.length - 1]
    if (!last) return
    this.setData({ scrollInto: 'msg-' + last.id })
  },

  onDraft: function (e) {
    this.setData({ draft: e.detail.value })
  },

  send: function (content) {
    const self = this
    const text = String(content || '').trim()
    if (!text || this.data.sending) return
    const msg = { id: 'local-' + Date.now(), from: 'me', type: 'text', content: text, timeText: nowTime() }
    const messages = this.data.messages.concat([msg])
    this.setData({ messages: messages, draft: '', sending: true })
    this.scrollToEnd()

    if (this.data.convoId) {
      this.postMessage('text', text)
    } else if (this.data.peerId) {
      // 会话还没建好：先建会话再发
      this.startConvo(function () {
        self.postMessage('text', text)
      })
    } else {
      // 纯前端演示会话：1 秒后自动回复
      setTimeout(function () {
        const reply = {
          id: 'local-' + Date.now(),
          from: 'peer',
          type: 'text',
          content: '在的在的！随时可以面交，你看今晚 8 点方便吗？😊',
          timeText: nowTime()
        }
        self.setData({ messages: self.data.messages.concat([reply]), sending: false })
        self.scrollToEnd()
      }, 1200)
    }
  },

  /** 把消息提交到后端，并用返回的完整记录覆盖本地气泡 */
  postMessage: function (type, content) {
    const self = this
    return http.post('/messages/' + this.data.convoId, { type: type, content: content }, { silent: true }).then(function (res) {
      const msgs = (res.messages || []).map(function (m) {
        m.timeText = shortTime(m.createdAt)
        m.id = String(m.id)
        return m
      })
      // 同步轮询基线：自己刚发的消息不再被轮询当成「新消息」重复滚动
      self._lastMsgId = msgs.length ? msgs[msgs.length - 1].id : ''
      self.setData({ messages: msgs, sending: false })
      self.scrollToEnd()
    }).catch(function () {
      self.setData({ sending: false })
      mp.showToast({ title: '发送失败，请重试', icon: 'none' })
    })
  },

  onSend: function () {
    this.send(this.data.draft)
  },

  /** ⊕ 附件面板：发送图片 / 发送视频 */
  onPlus: function () {
    const self = this
    mp.showActionSheet({
      itemList: ['发送图片', '发送视频'],
      success: function (res) {
        self.chooseMedia(res.tapIndex === 0 ? 'image' : 'video')
      }
    })
  },

  /** 选一张图片或一段视频 */
  chooseMedia: function (type) {
    const self = this
    mp.chooseMedia({
      count: 1,
      mediaType: [type],
      sizeType: ['compressed'],
      maxDuration: 60,
      success: function (res) {
        const f = res.tempFiles && res.tempFiles[0]
        if (!f || !f.tempFilePath) return
        self.sendMedia(type, f.tempFilePath, f.size)
      }
    })
  },

  /** 上传媒体文件并发送；先展示本地占位气泡，成功后由响应覆盖 */
  sendMedia: function (type, path, size) {
    const self = this
    if (this.data.sending) return
    if (type === 'video' && size && size > 50 * 1024 * 1024) {
      mp.showToast({ title: '视频不能超过 50MB，请截短后再发', icon: 'none' })
      return
    }
    const localId = 'local-' + Date.now()
    this.setData({
      messages: this.data.messages.concat([{
        id: localId, from: 'me', type: type, content: path, timeText: nowTime()
      }]),
      sending: true
    })
    this.scrollToEnd()

    // 纯前端演示会话（无会话 id、无对端）：本地展示 + 自动回复
    if (!this.data.convoId && !this.data.peerId) {
      setTimeout(function () {
        self.setData({
          sending: false,
          messages: self.data.messages.concat([{
            id: 'local-' + Date.now(), from: 'peer', type: 'text',
            content: '收到！图片/视频我看到了～', timeText: nowTime()
          }])
        })
        self.scrollToEnd()
      }, 1200)
      return
    }

    mp.showLoading({ title: type === 'video' ? '发送视频中' : '发送图片中', mask: true })
    const uploader = type === 'video' ? upload.uploadVideo : upload.uploadImage
    uploader(path).then(function (url) {
      mp.hideLoading()
      if (self.data.convoId) {
        return self.postMessage(type, url)
      }
      // 会话还没建好：先建会话再发
      return new Promise(function (resolve) {
        self.startConvo(function () {
          self.postMessage(type, url).then(resolve)
        })
      })
    }).catch(function () {
      mp.hideLoading()
      self.setData({ sending: false })
      // 上传失败：撤掉本地占位气泡
      self.setData({
        messages: self.data.messages.filter(function (m) { return m.id !== localId })
      })
    })
  },

  /** 点开图片消息大图预览 */
  previewMedia: function (e) {
    const url = e.currentTarget.dataset.url
    if (url) mp.previewImage({ current: url, urls: [url] })
  },

  /** 约面交：直接发一条约见消息（本平台无支付功能，只约校内当面看货） */
  onBargain: function () {
    const goods = this.data.goodsCard
    const title = goods && goods.title ? '「' + goods.title + '」' : '这件闲置'
    this.send('你好！我想看看' + title + '，方便约个时间在校内当面看看吗？😊')
  },

  /** 长按消息：微信同款，仅自己发的消息可在 2 分钟内撤回 */
  onLongPress: function (e) {
    const self = this
    const id = e.currentTarget.dataset.id
    const msg = this.data.messages.filter(function (m) { return m.id === id })[0]
    if (!msg || msg.from !== 'me') return
    if (!recallable(msg)) {
      mp.showToast({ title: '超过 2 分钟的消息无法撤回', icon: 'none' })
      return
    }
    mp.showModal({
      title: '撤回消息',
      content: '撤回后这条消息将从双方的聊天记录中消失。',
      confirmText: '撤回',
      confirmColor: '#ef4444',
      success: function (res) {
        if (res.confirm) self.doDelete(id)
      }
    })
  },

  doDelete: function (id) {
    const self = this
    // 本地未落库的消息（发消息后的极短窗口内长按）：直接本地移除即可
    if (String(id).indexOf('local-') === 0) {
      this.setData({
        messages: this.data.messages.filter(function (m) { return m.id !== id })
      })
      mp.showToast({ title: '已撤回消息', icon: 'none' })
      return
    }
    // 纯前端演示会话（无真实会话 id）没有后端可删，直接本地移除
    if (!this.data.convoId) {
      this.setData({
        messages: this.data.messages.filter(function (m) { return m.id !== id })
      })
      mp.showToast({ title: '已撤回消息', icon: 'none' })
      return
    }
    http.del('/messages/' + this.data.convoId + '/messages/' + id, null, { silent: true }).then(function () {
      self.setData({
        messages: self.data.messages.filter(function (m) { return m.id !== id })
      })
      mp.showToast({ title: '已撤回消息', icon: 'none' })
    }).catch(function (err) {
      const msg = err && err.msg ? err.msg : ''
      mp.showToast({ title: msg || '撤回失败，请重试', icon: 'none' })
    })
  },

  onSafety: function () {
    mp.showModal({
      title: '平台安全提示',
      content: '同校交易请约定公共场所面交，谨防钓鱼链接与脱离平台的私下转账。',
      showCancel: false,
      confirmText: '知道了'
    })
  },

  goBack: function () {
    mp.navigateBack()
  }
})

function nowTime() {
  const d = new Date()
  const p = function (n) { return n < 10 ? '0' + n : '' + n }
  return '今天 ' + p(d.getHours()) + ':' + p(d.getMinutes())
}

function shortTime(t) {
  if (!t) return ''
  const d = new Date(String(t).replace(/-/g, '/'))
  const p = function (n) { return n < 10 ? '0' + n : '' + n }
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return '今天 ' + p(d.getHours()) + ':' + p(d.getMinutes())
  }
  return timeAgo(t)
}

/** 日期分割线：取首条消息的日期，兼容 iOS 的 '-' 解析 */
function dayDivider(msgs) {
  if (!msgs.length || !msgs[0].createdAt) return '今天'
  const d = new Date(String(msgs[0].createdAt).replace(/-/g, '/'))
  if (isNaN(d.getTime())) return '今天'
  const p = function (n) { return n < 10 ? '0' + n : '' + n }
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return '今天'
  if (d.toDateString() === new Date(now.getTime() - 86400000).toDateString()) return '昨天'
  return (d.getMonth() + 1) + '月' + d.getDate() + '日'
}

/** 消息距现在的毫秒数；无时间戳（本地未落库消息）返回 -1 */
function msgAge(createdAt) {
  if (!createdAt) return -1
  const t = new Date(String(createdAt).replace(/-/g, '/'))
  if (isNaN(t.getTime())) return -1
  return Date.now() - t.getTime()
}

/** 是否在可撤回窗口内（微信同款 2 分钟）；本地未落库消息视为可撤回 */
function recallable(msg) {
  const age = msgAge(msg.createdAt)
  if (age < 0) return true
  return age <= 2 * 60 * 1000
}
