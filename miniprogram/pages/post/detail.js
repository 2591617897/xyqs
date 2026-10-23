const http = require('../../utils/request')
const { timeAgo, mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')

Page({
  data: {
    post: null,
    loading: true,
    loadError: false,
    liked: false,
    comments: [],
    commentInput: '',
    sending: false
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
      post.avatarText = (post.authorName || '校').substring(0, 1)
      self.setData({ post: post, loading: false, liked: !!post.liked })
      mp.setNavigationBarTitle({ title: post.channelName || '帖子详情' })
    }).catch(function () {
      self.setData({ loading: false, loadError: true })
    })
  },

  retry: function () {
    this.load()
    this.loadComments()
  },

  loadComments: function () {
    const self = this
    http.get('/posts/' + this.postId + '/comments', null, { silent: true }).then(function (list) {
      self.setData({
        comments: (list || []).map(function (c) {
          c.timeText = timeAgo(c.createdAt)
          c.avatarText = (c.userName || '校').substring(0, 1)
          return c
        })
      })
    }).catch(function () {})
  },

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

  onCommentInput: function (e) {
    this.setData({ commentInput: e.detail.value })
  },

  sendComment: function () {
    const self = this
    if (!this.data.commentInput.trim()) {
      toast('写点内容吧')
      return
    }
    if (this.data.sending) return
    this.setData({ sending: true })
    auth.ensureVerified().then(function () {
      return http.post('/posts/' + self.postId + '/comments', { content: self.data.commentInput.trim() })
    }).then(function () {
      self.setData({ commentInput: '', sending: false })
      self.loadComments()
      const post = self.data.post
      post.commentCount = (post.commentCount || 0) + 1
      self.setData({ post: post })
    }).catch(function () {
      self.setData({ sending: false })
    })
  },

  previewImage: function (e) {
    mp.previewImage({
      current: e.currentTarget.dataset.url,
      urls: this.data.post.imageList || []
    })
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
          setTimeout(function () {
            mp.navigateBack()
          }, 600)
        }).catch(function () {})
      }
    })
  }
})
