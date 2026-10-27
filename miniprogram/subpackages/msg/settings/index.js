const http = require('../../../utils/request')
const subscribe = require('../../../utils/subscribe')
const { mp, toast } = require('../../../utils/util')

/** 五类站内提醒。顺序与消息中心四宫格 + 私信保持一致，用户对得上号 */
const SWITCH_DEF = [
  { key: 'notifyMessage', title: '私信', sub: '有人给你发消息' },
  { key: 'notifyLike', title: '赞与收藏', sub: '闲置或动态被点赞、收藏' },
  { key: 'notifyFollow', title: '新增粉丝', sub: '有人关注你' },
  { key: 'notifyComment', title: '评论与@', sub: '评论、回复、被 @ 到' },
  { key: 'notifySystem', title: '官方通知', sub: '平台公告与安全提醒' }
]

/** 连续拨开关时合并成一次请求 */
const SAVE_DELAY = 300

Page({
  data: {
    loading: true,
    switches: SWITCH_DEF.map(function (d) {
      return { key: d.key, title: d.title, sub: d.sub, on: true }
    }),
    dndEnabled: false,
    dndStart: '22:00',
    dndEnd: '07:00',

    /** 后端配置的订阅消息模板 id（空 = 还没配，只展示不请求授权） */
    templateIds: [],
    wxPushAvailable: false,
    wxPushOn: false,
    wxTip: '正在读取设置…',
    wxBtn: '开启'
  },

  onLoad: function () {
    this.load()
  },

  onUnload: function () {
    if (this._saveTimer) clearTimeout(this._saveTimer)
  },

  load: function () {
    const self = this
    http.get('/messages/settings', null, { silent: true })
      .then(function (res) {
        const d = (res && res.data) || {}
        self.applyFromServer(d)
      })
      .catch(function () {
        // 拉不到就按默认值展示，至少别让页面卡在 loading
        self.setData({ loading: false })
        self.refreshWxPush()
      })
  },

  applyFromServer: function (d) {
    const switches = SWITCH_DEF.map(function (def) {
      return {
        key: def.key,
        title: def.title,
        sub: def.sub,
        on: d[def.key] !== false
      }
    })
    this.setData({
      loading: false,
      switches: switches,
      dndEnabled: !!d.dndEnabled,
      dndStart: d.dndStart || '22:00',
      dndEnd: d.dndEnd || '07:00',
      templateIds: d.templateIds || [],
      wxPushAvailable: !!d.wxPushAvailable,
      wxPushOn: !!d.wxPushOn
    })
    this.refreshWxPush()
  },

  /** 微信推送那一行的按钮文案与说明，全部在这里算好，WXML 只做插值（避免嵌套三目） */
  refreshWxPush: function () {
    let btn = '开启'
    let tip = ''
    if (!this.data.wxPushAvailable) {
      btn = '待开通'
      tip = '微信提醒还没开通，开启后可在微信「服务通知」收到提醒'
    } else if (this.data.wxPushOn) {
      btn = '已开启'
      tip = '已开启，重要消息会推送到微信「服务通知」'
    } else {
      tip = '开启后，重要消息会推送到微信「服务通知」'
    }
    this.setData({ wxBtn: btn, wxTip: tip })
  },

  /* ---------------- 站内开关 ---------------- */

  onToggle: function (e) {
    const key = e.currentTarget.dataset.key
    const on = !!e.detail.value
    const idx = this.data.switches.findIndex(function (s) { return s.key === key })
    if (idx < 0) return
    const prev = this.data.switches[idx].on
    const patch = {}
    patch['switches[' + idx + '].on'] = on
    this.setData(patch)
    this.queueSave({ key: key, on: on, prev: prev, idx: idx })
  },

  onDndToggle: function (e) {
    const on = !!e.detail.value
    const prev = this.data.dndEnabled
    this.setData({ dndEnabled: on })
    this.queueSave({ dnd: true, prev: prev })
  },

  onDndStart: function (e) {
    const v = e.detail.value
    const prev = this.data.dndStart
    this.setData({ dndStart: v })
    this.queueSave({ dnd: true, prev: prev, timeField: 'dndStart' })
  },

  onDndEnd: function (e) {
    const v = e.detail.value
    const prev = this.data.dndEnd
    this.setData({ dndEnd: v })
    this.queueSave({ dnd: true, prev: prev, timeField: 'dndEnd' })
  },

  /**
   * 合并保存。
   *
   * 连拨三个开关就发三次 PUT 没必要，也没意义——中间态对服务端没有价值。
   * 300ms 内的改动合并成一次，失败时按最后一次的 prev 回滚（乐观更新）。
   */
  queueSave: function (ctx) {
    const self = this
    this._pending = ctx
    if (this._saveTimer) clearTimeout(this._saveTimer)
    this._saveTimer = setTimeout(function () {
      self.flushSave()
    }, SAVE_DELAY)
  },

  flushSave: function () {
    const self = this
    const payload = this.buildPayload()
    http.put('/messages/settings', payload, { silent: true })
      .then(function () {
        self._pending = null
      })
      .catch(function () {
        const p = self._pending
        self._pending = null
        if (p && p.key) {
          const patch = {}
          patch['switches[' + p.idx + '].on'] = p.prev
          self.setData(patch)
        } else if (p && p.dnd) {
          const patch = {}
          if (p.timeField) patch[p.timeField] = p.prev
          else patch.dndEnabled = p.prev
          self.setData(patch)
        }
        toast('保存失败，已还原')
      })
  },

  buildPayload: function () {
    const payload = {
      dndEnabled: this.data.dndEnabled,
      dndStart: this.data.dndStart,
      dndEnd: this.data.dndEnd
    }
    this.data.switches.forEach(function (s) {
      payload[s.key] = s.on
    })
    return payload
  },

  /* ---------------- 微信订阅消息 ---------------- */

  /**
   * 开启 / 关闭微信推送。
   *
   * ⚠️ 这里的 subscribe.request 必须在**点击的同步调用栈**里发起，
   * 前面不能有 await 或任何网络请求 —— 否则微信会判「非用户点击触发」直接失败。
   * 所以模板 id 用 onLoad 已经拿到的 this.data.templateIds，绝不在这里现拉。
   */
  onSubscribe: function () {
    const self = this
    if (!this.data.wxPushAvailable) {
      toast('微信提醒待开通：绑定服务号并配置订阅模板后可用')
      return
    }
    if (this.data.wxPushOn) {
      mp.showModal({
        title: '关闭微信提醒',
        content: '关闭后，重要消息不再推送到微信「服务通知」，站内消息不受影响。',
        confirmText: '关闭',
        success: function (res) {
          if (!res.confirm) return
          self.reportSubscribe(0)
        }
      })
      return
    }

    subscribe.request(this.data.templateIds).then(function (r) {
      if (r.reason === 'no-template') {
        toast('微信提醒待开通')
        return
      }
      if (r.reason === 'unsupported') {
        toast('当前环境不支持微信提醒')
        return
      }
      if (r.reason === 'fail') {
        // 最常见：模板 id 没在后台配好、或额度已用尽
        toast('开启失败，请稍后再试')
        return
      }
      if (r.status === 2) {
        toast('你拒绝了授权，可稍后再开启')
      }
      self.reportSubscribe(r.status)
    })
  },

  reportSubscribe: function (status) {
    const self = this
    http.post('/messages/settings/subscribe?status=' + status, {}, { silent: true })
      .then(function (res) {
        const d = (res && res.data) || {}
        self.setData({
          wxPushOn: !!d.wxPushOn,
          wxPushAvailable: d.wxPushAvailable !== undefined ? !!d.wxPushAvailable : self.data.wxPushAvailable
        })
        self.refreshWxPush()
        mp.showToast({ title: status === 1 ? '已开启微信提醒' : '已关闭微信提醒', icon: 'none' })
      })
      .catch(function () {
        toast('保存失败，请重试')
      })
  }
})
