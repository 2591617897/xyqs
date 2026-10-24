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
    replyTo: null
  },

  onLoad: function (options) {
    this.postId = options.id
    this.load()
    this.loadComments()
  },

  load: function () {
    const self = this
    http.get('/posts/' + this.postId, null, { silent: true }).then(function (post) {
      post.timeText = timeAgo(post.createdAt)
      post.imageList = resolveList(post.imageList)
      // 头像快照可能落后于最新资料，自己的帖子用本地头像兜底
      const me = getApp().globalData.user
      if (post.mine && !post.authorAvatar && me && me.avatar) {
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

  closeCommentSheet: function () {
    this.setData({
      showCommentSheet: false,
      commentSheetFocus: false,
      showEmoji: false,
      replyTo: null
    })
  },

  insertAt: function () {
    const next = this.data.commentInput + '@'
    this.setData({ commentInput: next, commentSheetFocus: true })
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
      const payload = { content: content, images: urls }
      if (self.data.replyTo) payload.parentId = self.data.replyTo.id
      return http.post('/posts/' + self.postId + '/comments', payload)
    }).then(function () {
      self.setData({
        commentInput: '',
        commentImages: [],
        showEmoji: false,
        sending: false,
        replyTo: null,
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

  previewCommentImage: function (e) {
    const url = e.currentTarget.dataset.url
    mp.previewImage({ current: url, urls: [url] })
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
