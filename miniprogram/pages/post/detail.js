const http = require('../../utils/request')
const { timeAgo, mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')
const { resolveList } = require('../../utils/img')

const EMOJIS = [
  '😀', '😃', '😄', '😁', '😊', '🥰', '😍', '😘',
  '😜', '🤪', '😳', '🥺', '😭', '😅', '😴', '🤔',
  '🙃', '😇', '🤗', '😎', '🥳', '😤', '😱', '🤯',
  '👍', '👏', '🙏', '💪', '🤝', '✌️', '👀', '🎉',
  '❤️', '💔', '💌', '🌙', '⭐', '🌸', '🍀', '🌈'
]

Page({
  data: {
    post: null,
    loading: true,
    loadError: false,
    liked: false,
    comments: [],
    commentSort: 'default',
    commentInput: '',
    commentImages: [],
    showCommentSheet: false,
    commentSheetFocus: false,
    showEmoji: false,
    emojiList: EMOJIS,
    sending: false,
    replyTo: null,
    /** @ 选择器 */
    mentionUserIds: [],
    mentionPickerList: [],
    showMentionPicker: false
  },

  onLoad: function (options) {
    this.postId = options.id
    this.load()
    this.loadComments()
  },

  /** 底部分享按钮 / 右上角菜单转发 */
  onShareAppMessage: function () {
    const post = this.data.post
    const title = post
      ? (post.title || (post.content || '校园趣事').substring(0, 30))
      : '校园趣事'
    return {
      title: title,
      path: '/pages/post/detail?id=' + this.postId
    }
  },

  load: function () {
    const self = this
    http.get('/posts/' + this.postId, null, { silent: true }).then(function (post) {
      post.timeText = timeAgo(post.createdAt)
      post.imageList = resolveList(post.imageList)
      // 与首页信息流同一口径：只有拿得到「我的学校」和「帖子学校」时才区分，
      // 全市范围下外校同学的帖子要标「同市」，不能一律写「同校」
      const me = getApp().globalData.user || mp.getStorageSync('user') || {}
      post.schoolBadge = ''
      if (me.schoolId && post.schoolId) {
        post.schoolBadge = String(me.schoolId) === String(post.schoolId) ? '同校' : '同市'
      }
      // 头像快照可能落后于最新资料，自己的帖子用本地头像兜底
      if (post.mine && !post.authorAvatar && me.avatar) {
        post.authorAvatar = me.avatar
      }
      self.setData({ post: post, loading: false, loadError: false, liked: !!post.liked })
      mp.setNavigationBarTitle({ title: post.channelName || '帖子详情' })
    }).catch(function (err) {
      self.setData({ loading: false, loadError: !(err && err.code === 404) })
    })
  },

  loadComments: function () {
    const self = this
    http.get('/posts/' + this.postId + '/comments', null, { silent: true }).then(function (list) {
      const arr = (list || []).map(function (c) {
        c.timeText = timeAgo(c.createdAt)
        c.imageList = resolveList(c.imageList)
        c.expanded = false
        c.replyCount = c.replyCount || (c.replies || []).length
        c.replies = (c.replies || []).map(function (r) {
          r.timeText = timeAgo(r.createdAt)
          r.imageList = resolveList(r.imageList)
          return r
        })
        // WXML 不支持数组方法，默认展示的回复列表在 JS 里预计算
        c.repliesShown = c.replies.slice(0, 5)
        return c
      })
      if (self.data.commentSort === 'newest') arr.reverse()
      self.setData({ comments: arr })
    }).catch(function () {})
  },

  retry: function () {
    this.setData({ loading: true, loadError: false })
    this.load()
    this.loadComments()
  },

  /** 作者栏跳转：进对方个人主页（catchtap 已阻止冒泡） */
  goUserSpace: function () {
    const post = this.data.post
    if (!post || !post.userId) return
    mp.navigateTo({ url: '/pages/user/space/space?id=' + post.userId })
  },

  /* ---------- 评论排序 ---------- */
  switchSort: function (e) {
    const sort = e.currentTarget.dataset.sort
    if (sort === this.data.commentSort) return
    const list = this.data.comments.slice()
    if (sort === 'newest') list.reverse()
    this.setData({ commentSort: sort, comments: list })
  },

  /* ---------- 楼中楼回复 ---------- */
  replyComment: function (e) {
    const ds = e.currentTarget.dataset
    this.setData({
      replyTo: { id: ds.id, name: ds.name },
      showEmoji: false
    })
    this.openCommentSheet()
  },

  cancelReply: function () {
    this.setData({ replyTo: null })
  },

  toggleReplies: function (e) {
    const index = e.currentTarget.dataset.index
    const comment = this.data.comments[index]
    const expanded = !comment.expanded
    const patch = {}
    patch['comments[' + index + '].expanded'] = expanded
    patch['comments[' + index + '].repliesShown'] = expanded ? comment.replies : comment.replies.slice(0, 5)
    this.setData(patch)
  },

  /* ---------- 点赞 ---------- */
  toggleLike: function () {
    const self = this
    const post = this.data.post
    auth.ensureLogin().then(function () {
      return http.post('/posts/' + self.postId + '/like')
    }).then(function (liked) {
      post.liked = liked
      post.likeCount = Math.max(0, (post.likeCount || 0) + (liked ? 1 : -1))
      self.setData({ post: post, liked: liked })
    }).catch(function () {})
  },

  /* ---------- 评论抽屉 ---------- */
  focusInput: function () {
    this.setData({ commentSheetFocus: true, showEmoji: false })
  },

  openCommentSheet: function () {
    this.setData({ showCommentSheet: true, commentSheetFocus: false, showEmoji: false })
    const self = this
    setTimeout(function () {
      self.setData({ commentSheetFocus: true })
    }, 120)
  },

  /* 空实现：弹层容器 catchtap 阻止冒泡，避免「无 stop 方法」告警 */
  stop: function () {},

  closeCommentSheet: function () {
    this.setData({
      showCommentSheet: false,
      commentSheetFocus: false,
      showEmoji: false,
      replyTo: null,
      mentionUserIds: []
    })
  },

  openMentionPicker: function () {
    const self = this
    mp.showLoading({ title: '加载好友中', mask: true })
    http.get('/user/follow/list', { type: 'following' }, { silent: true }).then(function (res) {
      mp.hideLoading()
      const list = (res && res.list) ? res.list : []
      const filtered = list.filter(function (u) { return !self.data.mentionUserIds.some(function (m) { return m === u.id }) })
      if (!filtered.length) {
        mp.showToast({ title: '暂无可@的好友', icon: 'none' })
        return
      }
      self.setData({ mentionPickerList: filtered, showMentionPicker: true, commentSheetFocus: false, showEmoji: false })
    }).catch(function () {
      mp.hideLoading()
      mp.showToast({ title: '加载失败', icon: 'none' })
    })
  },

  closeMentionPicker: function () {
    const self = this
    this.setData({ showMentionPicker: false, mentionPickerList: [] })
    setTimeout(function () { self.setData({ commentSheetFocus: true }) }, 100)
  },

  confirmMention: function (e) {
    const self = this
    const idx = e.currentTarget.dataset.index
    const user = this.data.mentionPickerList[idx]
    if (!user) return
    const newIds = this.data.mentionUserIds.concat([user.id])
    const remaining = this.data.mentionPickerList.filter(function (_, i) { return i !== idx })
    const next = this.data.commentInput + '@' + (user.nickname || user.name || '校友') + ' '
    this.setData({
      mentionUserIds: newIds,
      mentionPickerList: remaining,
      commentInput: next,
      showMentionPicker: false
    })
    setTimeout(function () { self.setData({ commentSheetFocus: true }) }, 120)
  },

  onCommentInput: function (e) {
    this.setData({ commentInput: e.detail.value })
  },

  toggleEmoji: function () {
    const willShow = !this.data.showEmoji
    this.setData({ showEmoji: willShow, commentSheetFocus: false })
    if (!willShow) {
      const self = this
      setTimeout(function () { self.setData({ commentSheetFocus: true }) }, 100)
    }
  },

  addEmoji: function (e) {
    const next = this.data.commentInput + e.currentTarget.dataset.emoji
    if (next.length > 500) {
      toast('评论最多 500 字')
      return
    }
    this.setData({ commentInput: next })
  },

  chooseCommentImage: function () {
    const self = this
    const rest = 3 - this.data.commentImages.length
    if (rest <= 0) {
      toast('最多 3 张图片')
      return
    }
    mp.chooseImage({
      count: rest,
      sizeType: ['compressed'],
      success: function (res) {
        self.setData({
          commentImages: self.data.commentImages.concat(res.tempFilePaths),
          showEmoji: false
        })
      }
    })
  },

  removeCommentImage: function (e) {
    const list = this.data.commentImages.slice()
    list.splice(e.currentTarget.dataset.index, 1)
    this.setData({ commentImages: list })
  },

  sendComment: function () {
    const self = this
    const content = this.data.commentInput.trim()
    const images = this.data.commentImages
    if (!content && !images.length) {
      toast('说点什么，或者配张图吧')
      return
    }
    if (this.data.sending) {
      toast('正在发送，请稍候')
      return
    }
    this.setData({ sending: true })
    if (images.length) mp.showLoading({ title: '上传图片中', mask: true })
    auth.ensureVerified().then(function () {
      return upload.uploadImages(images)
    }).then(function (urls) {
      if (images.length) mp.hideLoading()
      const payload = { content: content, images: urls, mentionUserIds: self.data.mentionUserIds }
      if (self.data.replyTo) payload.parentId = self.data.replyTo.id
      return http.post('/posts/' + self.postId + '/comments', payload)
    }).then(function () {
      self.setData({
        commentInput: '',
        commentImages: [],
        showEmoji: false,
        sending: false,
        replyTo: null,
        mentionUserIds: [],
        showCommentSheet: false,
        commentSheetFocus: false
      })
      self.loadComments()
      self.load()
      mp.setStorageSync('needRefreshPosts', 1)
    }).catch(function () {
      self.setData({ sending: false })
      mp.hideLoading()
    })
  },

  deleteComment: function (e) {
    const self = this
    const id = e.currentTarget.dataset.id
    mp.showModal({
      title: '删除这条评论？',
      content: '删除后不可恢复',
      confirmColor: '#ff4d4f',
      success: function (res) {
        if (!res.confirm) return
        http.del('/posts/' + self.postId + '/comments/' + id).then(function () {
          toast('已删除')
          self.loadComments()
          self.load()
          mp.setStorageSync('needRefreshPosts', 1)
        }).catch(function () {})
      }
    })
  },

  previewImage: function (e) {
    mp.previewImage({
      current: e.currentTarget.dataset.url,
      urls: this.data.post.imageList || []
    })
  },

  previewComment: function (e) {
    const ds = e.currentTarget.dataset
    const comment = this.data.comments[ds.pindex]
    if (!comment) return
    // 楼中楼图片优先用该条回复自己的图集（带 rindex），否则用主评论图集
    let urls = comment.imageList || []
    if (ds.rindex !== undefined) {
      const reply = (comment.repliesShown || [])[ds.rindex]
      if (reply && reply.imageList && reply.imageList.length) urls = reply.imageList
    }
    if (!urls.length) {
      mp.previewImage({ current: ds.url, urls: [ds.url] })
      return
    }
    mp.previewImage({ current: ds.url, urls: urls })
  },

  /* ---------- 自己的帖子：编辑 / 删除 ---------- */
  openMore: function () {
    const self = this
    if (!this.data.post || !this.data.post.mine) return
    mp.showActionSheet({
      itemList: ['编辑帖子', '删除帖子'],
      success: function (res) {
        if (res.tapIndex === 0) self.goEdit()
        else if (res.tapIndex === 1) self.deletePost()
      }
    })
  },

  goEdit: function () {
    mp.navigateTo({ url: '/pages/post/publish?id=' + this.postId })
  },

  deletePost: function () {
    const self = this
    mp.showModal({
      title: '删除这篇帖子？',
      content: '删除后不可恢复',
      confirmColor: '#ff4d4f',
      success: function (res) {
        if (!res.confirm) return
        http.del('/posts/' + self.postId).then(function () {
          toast('已删除')
          mp.setStorageSync('needRefreshPosts', 1)
          setTimeout(function () {
            mp.navigateBack()
          }, 600)
        }).catch(function () {})
      }
    })
  }
})
