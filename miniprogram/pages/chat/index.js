const http = require('../../utils/request')
const { mp, timeAgo } = require('../../utils/util')

Page({
  data: {
    convoId: '',
    peerName: '同学',
    goodsCard: null,
    messages: [],
    draft: '',
    quicks: ['🐣 我想小刀 ¥100', '📍 约定面交时间地点', '🏫 发宿舍定位'],
    sending: false,
    scrollInto: ''
  },

  onLoad: function (options) {
    mp.setNavigationBarTitle({ title: '私信' })
    this.setData({
      convoId: options.id || '',
      peerId: options.peer || '',
      goodsId: options.gid || '',
      goodsPrice: options.gp ? decodeURIComponent(options.gp) : '',
      goodsEmoji: options.ge ? decodeURIComponent(options.ge) : '',
      goodsStatus: options.gs ? decodeURIComponent(options.gs) : '',
      peerName: options.name ? decodeURIComponent(options.name) : '同学',
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
      goodsStatus: this.data.goodsStatus === '' ? undefined : Number(this.data.goodsStatus)
    }, { silent: true }).then(function (convo) {
      self.setData({
        convoId: convo.id,
        peerName: convo.peerName || self.data.peerName,
        goodsCard: convo.goodsCard || self.data.goodsCard
      })
      if (done) done()
    }).catch(function () {
      mp.showToast({ title: '会话创建失败', icon: 'none' })
    })
  },

  applyConvo: function (res) {
    const msgs = (res.messages || []).map(function (m) {
      m.timeText = shortTime(m.createdAt)
      return m
    })
    this.setData({
      peerName: res.peerName || this.data.peerName,
      goodsCard: res.goodsCard || this.data.goodsCard,
      messages: msgs
    })
    this.scrollToEnd()
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
      this.postMessage(text)
    } else if (this.data.peerId) {
      // 会话还没建好：先建会话再发
      this.startConvo(function () {
        self.postMessage(text)
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
  postMessage: function (text) {
    const self = this
    http.post('/messages/' + this.data.convoId, { content: text }, { silent: true }).then(function (res) {
      const msgs = (res.messages || []).map(function (m) {
        m.timeText = shortTime(m.createdAt)
        return m
      })
      self.setData({ messages: msgs, sending: false })
      self.scrollToEnd()
    }).catch(function () {
      self.setData({ sending: false })
    })
  },

  onSend: function () {
    this.send(this.data.draft)
  },

  onQuick: function (e) {
    const text = e.currentTarget.dataset.text.replace(/^[^\u4e00-\u9fa5A-Za-z0-9¥]+/, '')
    this.send(text)
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
