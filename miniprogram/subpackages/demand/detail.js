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
const { mp, timeAgo, toast, priceText } = require('../../utils/util')
const { resolveList, resolve, imgMode, imgGridClass } = require('../../utils/img')
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
    ctaText: '我有货',
    /** 互通推荐：广场上可能符合这条需求的在售闲置 */
    matches: [],
    /** 评论区：与频道（帖子详情）同一套两层结构 */
    comments: [],
    commentSort: 'default',
    commentInput: '',
    commentParentId: 0,
    commentPlaceholder: '说点什么…',
    commentSubmitting: false,
    showCommentSheet: false
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
      // 互通推荐：推荐失败不影响主流程（卡片区不渲染而已），所以单独 catch
      self.loadMatches()
      // 评论公开可见，任何浏览者都能看；失败同样只是评论区空，不拖垮详情
      self.loadComments()
    }).catch(function () {
      self.setData({ loading: false, loadError: true })
    })
  },

  /**
   * 「有人在找自行车」→ 推他可能想买的在售闲置。
   * 只对本人和同校/同市可见内容做匹配（后端按 viewerSchoolId + visibility 过滤过）。
   */
  loadMatches: function () {
    const self = this
    http.get('/demands/' + this.data.id + '/matches', null, { silent: true })
      .then(function (list) {
        self.setData({
          matches: (list || []).map(function (g) {
            g.priceText = priceText(g.price)
            g.cover = resolve((g.imageList || [])[0] || '')
            return g
          })
        })
      })
      .catch(function () {})
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

  /* ==================== 评论 ====================
     与频道（帖子详情）同构：两层树（顶层评论 + 楼中楼），
     点赞 / 回复 / 删除都按评论 id 走。 */

  loadComments: function () {
    const self = this
    http.get('/demands/' + this.data.id + '/comments', null, { silent: true })
      .then(function (list) {
        const arr = (list || []).map(function (c) {
          c.timeText = timeAgo(c.createdAt)
          c.imageList = resolveList(c.imageList)
          c.expanded = false
          c.likeCount = c.likeCount || 0
          c.liked = !!c.liked
          c.replyCount = c.replyCount || (c.replies || []).length
          c.replies = (c.replies || []).map(function (r) {
            r.timeText = timeAgo(r.createdAt)
            r.imageList = resolveList(r.imageList)
            r.likeCount = r.likeCount || 0
            r.liked = !!r.liked
            return r
          })
          // WXML 不支持数组方法，默认展示的前 5 条在 JS 里预计算
          c.repliesShown = c.replies.slice(0, 5)
          return c
        })
        if (self.data.commentSort === 'newest') arr.reverse()
        self.setData({ comments: arr })
      })
      .catch(function () {})
  },

  switchSort: function (e) {
    const sort = e.currentTarget.dataset.sort
    if (sort === this.data.commentSort) return
    const list = this.data.comments.slice()
    if (sort === 'newest') list.reverse()
    else list.reverse()
    this.setData({ commentSort: sort, comments: list })
  },

  openCommentSheet: function () {
    this.setData({
      showCommentSheet: true,
      commentParentId: 0,
      commentPlaceholder: '说点什么…'
    })
  },

  closeCommentSheet: function () {
    this.setData({ showCommentSheet: false })
  },

  onCommentInput: function (e) {
    this.setData({ commentInput: e.detail.value || '' })
  },

  replyComment: function (e) {
    const ds = e.currentTarget.dataset
    this.setData({
      showCommentSheet: true,
      commentParentId: ds.id,
      commentPlaceholder: '回复 ' + (ds.name || '校园用户') + '：'
    })
  },

  sendComment: function () {
    const self = this
    const text = String(this.data.commentInput || '').trim()
    if (!text) {
      toast('说点什么吧')
      return
    }
    if (this.data.commentSubmitting) return
    this.setData({ commentSubmitting: true })
    http.post('/demands/' + this.data.id + '/comments', {
      content: text,
      parentId: this.data.commentParentId || 0
    }).then(function () {
      self.setData({
        showCommentSheet: false,
        commentInput: '',
        commentParentId: 0,
        commentPlaceholder: '说点什么…',
        commentSubmitting: false
      })
      toast('评论已发布')
      self.loadComments()
      // 只改计数，不整体 reload —— 否则页面会跳回顶部
      const info = self.data.info
      if (info) {
        self.setData({ 'info.commentCount': (info.commentCount || 0) + 1 })
      }
    }).catch(function () {
      self.setData({ commentSubmitting: false })
    })
  },

  /** 点赞 / 取消点赞一条评论（rindex 有值表示是楼中楼那条） */
  toggleCommentLike: function (e) {
    const self = this
    const ds = e.currentTarget.dataset
    const pindex = Number(ds.pindex)
    const rindex = ds.rindex === undefined ? undefined : Number(ds.rindex)
    const comment = this.data.comments[pindex]
    if (!comment) return
    const target = rindex === undefined ? comment : (comment.replies || [])[rindex]
    if (!target) return
    const id = target.id
    auth.ensureLogin().then(function () {
      return http.post('/demands/comments/' + id + '/like')
    }).then(function (liked) {
      const cnt = Math.max(0, (target.likeCount || 0) + (liked ? 1 : -1))
      const patch = {}
      if (rindex === undefined) {
        patch['comments[' + pindex + '].liked'] = liked
        patch['comments[' + pindex + '].likeCount'] = cnt
      } else {
        patch['comments[' + pindex + '].repliesShown[' + rindex + '].liked'] = liked
        patch['comments[' + pindex + '].repliesShown[' + rindex + '].likeCount'] = cnt
        patch['comments[' + pindex + '].replies[' + rindex + '].liked'] = liked
        patch['comments[' + pindex + '].replies[' + rindex + '].likeCount'] = cnt
      }
      self.setData(patch)
    }).catch(function () {})
  },

  deleteComment: function (e) {
    const self = this
    const id = e.currentTarget.dataset.id
    ui.confirm({ title: '删除这条评论？', content: '删除后不可恢复' }).then(function (ok) {
      if (!ok) return
      http.del('/demands/' + self.data.id + '/comments/' + id).then(function () {
        toast('已删除')
        self.loadComments()
        const info = self.data.info
        if (info) {
          self.setData({ 'info.commentCount': Math.max(0, (info.commentCount || 0) - 1) })
        }
      }).catch(function () {})
    })
  },

  /** 展开 / 收起楼中楼（默认只展示前 5 条） */
  toggleReplies: function (e) {
    const idx = Number(e.currentTarget.dataset.index)
    const comment = this.data.comments[idx]
    if (!comment) return
    const expanded = !comment.expanded
    const patch = {}
    patch['comments[' + idx + '].expanded'] = expanded
    patch['comments[' + idx + '].repliesShown'] = expanded
      ? comment.replies
      : comment.replies.slice(0, 5)
    this.setData(patch)
  },

  previewComment: function (e) {
    const ds = e.currentTarget.dataset
    const comment = this.data.comments[Number(ds.pindex)]
    if (!comment) return
    const target = ds.rindex === undefined
      ? comment
      : (comment.replies || [])[Number(ds.rindex)]
    const urls = (target && target.imageList) || []
    mp.previewImage({ current: ds.url || urls[0], urls: urls })
  },

  goCommentUser: function (e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    mp.navigateTo({ url: '/pages/user/space/space?id=' + id })
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
    mp.navigateTo({ url: '/pages/user/space/space?id=' + info.userId })
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

  goGoodsDetail: function (e) {
    mp.navigateTo({ url: '/pages/goods/detail?id=' + e.currentTarget.dataset.id })
  },

  previewImage: function (e) {
    const src = e.currentTarget.dataset.src
    const list = (this.data.info && this.data.info.imageList) || []
    if (!src) return
    mp.previewImage({ current: src, urls: list })
  }
})
