/**
 * 需求广场 · 详情
 *
 * 两端行为分叉的关键页：
 *  - 发布者：看回应列表（带联系方式）、标记已找到 / 下架 / 重新开放 / 编辑 / 删除
 *  - 其他人：看需求本身，底部「我有货·我能帮」+「私信 TA」
 *
 * 回应列表只有发布者能拉（后端也只认发布者）——里面是联系方式明文，
 * 不登录、非本人一律拿不到。
 */
const http = require('../../utils/request')
const { mp, timeAgo, toast } = require('../../utils/util')
const { resolveList, imgMode, imgGridClass } = require('../../utils/img')
const demandUtil = require('../../utils/demand')
const auth = require('../../utils/auth')
const ui = require('../../utils/ui')

Page({
  data: {
    id: '',
    info: null,
    replies: [],
    loading: true,
    loadError: false,
    needLogin: false,
    /** 回应弹层 */
    showReply: false,
    replyMessage: '',
    replyContact: '',
    submitting: false,
    /** 底部按钮文案随类型/状态变，在 js 里算好，不在 wxml 写三目链 */
    ctaText: '我有货'
  },

  onLoad: function (options) {
    this.setData({ id: options.id || '' })
    this.load()
  },

  onShow: function () {
    if (mp.getStorageSync('needRefreshDemand')) {
      mp.removeStorageSync('needRefreshDemand')
      this.load()
    }
  },

  load: function () {
    const self = this
    const id = this.data.id
    if (!id) {
      this.setData({ loading: false, loadError: true })
      return
    }
    this.setData({ loading: true, loadError: false })
    http.get('/demands/' + id, null, { silent: true }).then(function (info) {
      const decorated = demandUtil.decorate(info || {})
      decorated.imageList = resolveList(decorated.imageList)
      decorated.imgMode = imgMode()
      decorated.imgGrid = imgGridClass((decorated.imageList || []).length)
      decorated.timeText = timeAgo(decorated.createdAt)
      self.setData({
        info: decorated,
        loading: false,
        loadError: false,
        ctaText: decorated.type === 2 ? '我能帮' : '我有货'
      })
      mp.setNavigationBarTitle({ title: decorated.type === 2 ? '求帮忙详情' : '求物详情' })
      // 回应只有本人能看，非本人直接跳过（后端也会拒绝，这里省一次必然失败的请求）
      if (decorated.mine) self.loadReplies()
    }).catch(function () {
      self.setData({ loading: false, loadError: true })
    })
  },

  loadReplies: function () {
    const self = this
    http.get('/demands/' + this.data.id + '/replies', null, { silent: true })
      .then(function (list) {
        self.setData({
          replies: (list || []).map(function (r) {
            r.timeText = timeAgo(r.createdAt)
            r.statusText = demandUtil.REPLY_STATUS_TEXT[r.status] || ''
            return r
          })
        })
      })
      .catch(function () {})
  },

  retry: function () {
    this.load()
  },

  /* ==================== 回应（非发布者） ==================== */

  onTapReply: function () {
    const self = this
    auth.ensureVerified().then(function () {
      if (self.data.info && self.data.info.replied) {
        toast('你已经回应过这条需求了')
        return
      }
      self.setData({ showReply: true })
    }).catch(function () {})
  },

  closeReply: function () {
    this.setData({ showReply: false })
  },

  onReplyInput: function (e) {
    this.setData({ replyMessage: e.detail.value || '' })
  },

  onContactInput: function (e) {
    this.setData({ replyContact: e.detail.value || '' })
  },

  submitReply: function () {
    const self = this
    const msg = String(this.data.replyMessage || '').trim()
    if (!msg) {
      toast('说点什么，让对方知道你能提供什么')
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })
    http.post('/demands/' + this.data.id + '/replies', {
      message: msg,
      contact: String(this.data.replyContact || '').trim()
    }).then(function () {
      self.setData({ showReply: false, submitting: false, replyMessage: '', replyContact: '' })
      toast('已发送，等对方联系你')
      self.load()
    }).catch(function () {
      self.setData({ submitting: false })
    })
  },

  /* ==================== 私信 ==================== */

  goChat: function () {
    const self = this
    auth.ensureLogin().then(function () {
      const info = self.data.info || {}
      if (info.mine) {
        toast('这是你自己的需求')
        return
      }
      const name = encodeURIComponent(info.authorName || '同学')
      mp.navigateTo({
        url: '/pages/chat/index?peer=' + info.userId + '&name=' + name
      })
    }).catch(function () {})
  },

  goProfile: function () {
    const info = this.data.info || {}
    if (!info.userId) return
    mp.navigateTo({ url: '/pages/user/space/space?userId=' + info.userId })
  },

  /* ==================== 发布者操作 ==================== */

  markDone: function () {
    const self = this
    ui.confirm({ title: '标记为已完成？', content: '标记后这条需求不会再出现在广场上' }).then(function (ok) {
      if (!ok) return
      self.changeStatus(2, '已标记为已完成')
    })
  },

  takeOff: function () {
    const self = this
    ui.confirm({ title: '下架这条需求？', content: '下架后其他人看不到，你可以随时重新开放' }).then(function (ok) {
      if (!ok) return
      self.changeStatus(0, '已下架')
    })
  },

  reopen: function () {
    this.changeStatus(1, '已重新开放')
  },

  changeStatus: function (status, okMsg) {
    const self = this
    http.post('/demands/' + this.data.id + '/status', { status: status }).then(function () {
      toast(okMsg)
      mp.setStorageSync('needRefreshDemand', 1)
      self.load()
    }).catch(function () {})
  },

  edit: function () {
    mp.navigateTo({ url: '/subpackages/demand/publish?id=' + this.data.id })
  },

  remove: function () {
    const self = this
    ui.confirm({ title: '删除这条需求？', content: '删除后不可恢复，收到的回应也会一并删除' }).then(function (ok) {
      if (!ok) return
      http.del('/demands/' + self.data.id).then(function () {
        toast('已删除')
        mp.setStorageSync('needRefreshDemand', 1)
        setTimeout(function () { mp.navigateBack() }, 600)
      }).catch(function () {})
    })
  },

  previewImage: function (e) {
    const src = e.currentTarget.dataset.src
    const list = (this.data.info && this.data.info.imageList) || []
    if (!src) return
    mp.previewImage({ current: src, urls: list })
  }
})
