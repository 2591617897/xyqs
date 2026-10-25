const http = require('../../utils/request')
const { timeAgo, mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')
const { resolveList } = require('../../utils/img')

const AVATAR_EMOJI = ['🌸', '🌙', '🍀', '⭐', '🐳', '🍑', '🌿', '☁️']

const EMOJIS = [
  '😀', '😃', '😄', '😁', '😊', '🥰', '😍', '😘',
  '😜', '🤪', '😳', '🥺', '😭', '😅', '😴', '🤔',
  '🙃', '😇', '🤗', '😎', '🥳', '😤', '😱', '🤯',
  '👍', '👏', '🙏', '💪', '🤝', '✌️', '👀', '🎉',
  '❤️', '💔', '💌', '🌙', '⭐', '🌸', '🍀', '🌈'
]

Page({
  data: {
    id: null,
    post: null,
    loading: true,
    loadError: false,
    comments: [],
    commentInput: '',
    commentImages: [],
    showCommentSheet: false,
    commentSheetFocus: false,
    /** 键盘高度：抽屉定位在 bottom，键盘弹出时必须整体上移，否则输入框会被挡住 */
    keyboardHeight: 0,
    showEmoji: false,
    emojiList: EMOJIS,
    sending: false,
    replyTo: null,
    showEdit: false,
    editContent: '',
    editImages: [],
    saving: false,
    /** @ 选择器 */
    mentionUserIds: [],
    mentionPickerList: [],
    showMentionPicker: false
  },

  onLoad: function (options) {
    const id = options.id
    if (!id) {
      toast('参数错误')
      mp.navigateBack()
      return
    }
    this.setData({ id: id })
    this.load()
    this.loadComments()
  },

  onUnload: function () {
    mp.setStorageSync('needRefreshWall', 1)
  },

  /** 右上角菜单 / 底部分享按钮 */
  onShareAppMessage: function () {
    const post = this.data.post
    return {
      title: post ? (post.content || '匿名心事').substring(0, 30) : '校园聚 · 匿名心事',
      path: '/pages/wall/detail?id=' + this.data.id
    }
  },

  load: function () {
    const self = this
    http.get('/wall/posts/' + this.data.id, null, { silent: true })
      .then(function (post) {
        self.setData({ post: decorate(post), loading: false })
        mp.setNavigationBarTitle({ title: '匿名心事' })
      })
      .catch(function (err) {
        // 404 视为内容已删除，其余按网络/服务异常处理
        self.setData({ loading: false, loadError: !(err && err.code === 404) })
      })
  },

  retry: function () {
    this.setData({ loading: true, loadError: false })
    this.load()
    this.loadComments()
  },

  loadComments: function () {
    const self = this
    http.get('/wall/posts/' + this.data.id + '/comments', null, { silent: true })
      .then(function (list) {
        self.setData({
          comments: (list || []).map(function (c) {
            c.timeText = timeAgo(c.createdAt)
            c.avatarClass = 'anon-' + ((Math.abs(Number(c.id)) % 8) + 1)
            c.imageList = resolveList(c.imageList)
            c.expanded = false
            c.replyCount = c.replyCount || (c.replies || []).length
            c.replies = (c.replies || []).map(function (r) {
              r.timeText = timeAgo(r.createdAt)
              r.avatarClass = 'anon-' + ((Math.abs(Number(r.id)) % 8) + 1)
              r.imageList = resolveList(r.imageList)
              return r
            })
            // 注意：WXML 表达式不支持数组方法，默认展示的回复列表必须在 JS 里预计算
            c.repliesShown = c.replies.slice(0, 5)
            return c
          })
        })
      })
      .catch(function () {})
  },

  /* ---------- 点赞 ---------- */
  toggleLike: function () {
    const self = this
    const post = this.data.post
    if (!post) return
    auth.ensureLogin().then(function () {
      return http.post('/wall/posts/' + self.data.id + '/like')
    }).then(function (liked) {
      const patch = {}
      patch['post.liked'] = liked
      patch['post.likeCount'] = Math.max(0, (post.likeCount || 0) + (liked ? 1 : -1))
      self.setData(patch)
      mp.setStorageSync('needRefreshWall', 1)
    }).catch(function () {})
  },

  /* ---------- 图片预览 ---------- */
  preview: function (e) {
    const urls = this.data.post.imageList || []
    if (!urls.length) return
    mp.previewImage({ current: e.currentTarget.dataset.url, urls: urls })
  },

  previewComment: function (e) {
    const index = e.currentTarget.dataset.pindex
    const comment = this.data.comments[index]
    if (!comment || !comment.imageList.length) return
    mp.previewImage({
      current: e.currentTarget.dataset.url,
      urls: comment.imageList
    })
  },

  /* ---------- 评论抽屉 ---------- */
  /** 点「评论」：弹出底部回复框 */
  openCommentSheet: function () {
    this.setData({ showCommentSheet: true, showEmoji: false, commentSheetFocus: false })
    // 延迟一帧再聚焦，保证 false→true 跳变，WeChat 才会弹键盘
    const self = this
    setTimeout(function () {
      self.setData({ commentSheetFocus: true })
    }, 150)
  },

  closeCommentSheet: function () {
    this.setData({
      showCommentSheet: false,
      commentSheetFocus: false,
      showEmoji: false,
      replyTo: null,
      keyboardHeight: 0
    })
  },

  /**
   * 键盘高度变化 → 把抽屉整体上移，避免输入框和「发布」被键盘挡住。
   *
   * 抽屉是 `position: fixed; bottom: 0`，微信的 adjust-position 只会滚动页面、
   * 不会移动 fixed 元素，所以 textarea 上关掉 adjust-position，改用手动抬升。
   */
  onKeyboardHeight: function (e) {
    const h = (e.detail && e.detail.height) || 0
    if (h !== this.data.keyboardHeight) {
      this.setData({ keyboardHeight: h })
    }
  },

  onCommentInput: function (e) {
    this.setData({ commentInput: e.detail.value })
  },

  /** 点评论 / 回复上的「回复」：弹出抽屉并进入回复某人的状态 */
  replyComment: function (e) {
    const ds = e.currentTarget.dataset
    // 先置 false 再置 true：刚发过评论时输入框往往仍处于聚焦态，
    // 直接设 true 不会产生 false→true 跳变，WeChat 不会重新弹键盘。
    // 延迟一帧再聚焦，避免「点了回复却输入不了」。
    this.setData({
      replyTo: { id: ds.id, name: ds.name },
      showCommentSheet: true,
      showEmoji: false,
      commentSheetFocus: false
    })
    const self = this
    setTimeout(function () {
      self.setData({ commentSheetFocus: true })
    }, 150)
  },

  /** 展开 / 收起某条评论的全部回复 */
  toggleReplies: function (e) {
    const index = e.currentTarget.dataset.index
    const comment = this.data.comments[index]
    const expanded = !comment.expanded
    const patch = {}
    patch['comments[' + index + '].expanded'] = expanded
    patch['comments[' + index + '].repliesShown'] = expanded ? comment.replies : comment.replies.slice(0, 5)
    this.setData(patch)
  },

  deleteReply: function (e) {
    this.deleteComment(e)
  },

  toggleEmoji: function () {
    this.setData({ showEmoji: !this.data.showEmoji, commentSheetFocus: false })
  },

  addEmoji: function (e) {
    const emoji = e.currentTarget.dataset.emoji
    const next = this.data.commentInput + emoji
    if (next.length > 500) {
      toast('评论最多 500 字')
      return
    }
    this.setData({ commentInput: next })
  },

  openMentionPicker: function () {
    const self = this
    mp.showLoading({ title: '加载好友中', mask: true })
    http.get('/user/follow/list', { type: 'following' }, { silent: true }).then(function (res) {
      mp.hideLoading()
      // FollowController#list 返回 R<List<FollowUserVO>>，request 解包后是裸数组；
      // 这里同时兼容未来改成 {list:[]} 分页对象的情况
      const list = Array.isArray(res) ? res : ((res && res.list) || [])
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
      const payload = {
        content: content,
        images: urls,
        mentionUserIds: self.data.mentionUserIds
      }
      // 回复评论时带上父评论 id
      if (self.data.replyTo) payload.parentId = self.data.replyTo.id
      return http.post('/wall/posts/' + self.data.id + '/comments', payload)
    }).then(function () {
      self.setData({
        commentInput: '',
        commentImages: [],
        showCommentSheet: false,
        commentSheetFocus: false,
        showEmoji: false,
        sending: false,
        replyTo: null,
        keyboardHeight: 0,
        mentionUserIds: []
      })
      self.loadComments()
      const patch = {}
      patch['post.commentCount'] = (self.data.post.commentCount || 0) + 1
      self.setData(patch)
      mp.setStorageSync('needRefreshWall', 1)
    }).catch(function () {
      self.setData({ sending: false })
      mp.hideLoading()
    })
  },

  deleteComment: function (e) {
    const self = this
    const commentId = e.currentTarget.dataset.id
    mp.showModal({
      title: '删除这条评论？',
      content: '删除后不可恢复',
      confirmColor: '#ff4d4f',
      success: function (res) {
        if (!res.confirm) return
        http.del('/wall/posts/' + self.data.id + '/comments/' + commentId).then(function () {
          toast('已删除')
          self.loadComments()
          const patch = {}
          patch['post.commentCount'] = Math.max(0, (self.data.post.commentCount || 0) - 1)
          self.setData(patch)
          mp.setStorageSync('needRefreshWall', 1)
        }).catch(function () {})
      }
    })
  },

  /* ---------- 更多：编辑 / 删除帖子 ---------- */
  openMore: function () {
    const self = this
    const post = this.data.post
    if (!post || !post.mine) return
    mp.showActionSheet({
      itemList: ['编辑内容', '删除这条'],
      success: function (res) {
        if (res.tapIndex === 0) self.openEdit()
        else if (res.tapIndex === 1) self.removePost()
      }
    })
  },

  goReport: function () {
    const post = this.data.post
    if (!post) return
    mp.navigateTo({ url: '/pages/report/report?type=WALL&id=' + post.id })
  },

  openEdit: function () {
    const post = this.data.post
    this.setData({
      showEdit: true,
      editContent: post.content,
      editImages: (post.imageList || []).slice(0, 3)
    })
  },

  closeEdit: function () {
    this.setData({ showEdit: false })
  },

  onEditContent: function (e) {
    this.setData({ editContent: e.detail.value })
  },

  chooseEditImage: function () {
    const self = this
    const rest = 3 - this.data.editImages.length
    if (rest <= 0) {
      toast('最多 3 张图片')
      return
    }
    mp.chooseImage({
      count: rest,
      sizeType: ['compressed'],
      success: function (res) {
        self.setData({ editImages: self.data.editImages.concat(res.tempFilePaths) })
      }
    })
  },

  removeEditImage: function (e) {
    const list = this.data.editImages.slice()
    list.splice(e.currentTarget.dataset.index, 1)
    this.setData({ editImages: list })
  },

  saveEdit: function () {
    const self = this
    const content = this.data.editContent.trim()
    if (!content) {
      toast('内容不能为空')
      return
    }
    if (this.data.saving) return
    this.setData({ saving: true })
    if (this.data.editImages.length) mp.showLoading({ title: '上传图片中', mask: true })
    upload.uploadImages(this.data.editImages).then(function (urls) {
      if (self.data.editImages.length) mp.hideLoading()
      return http.put('/wall/posts/' + self.data.id, {
        content: content,
        images: urls
      }, { loading: '保存中' })
    }).then(function () {
      self.setData({ saving: false, showEdit: false })
      toast('已保存')
      self.load()
      mp.setStorageSync('needRefreshWall', 1)
    }).catch(function () {
      self.setData({ saving: false })
      mp.hideLoading()
    })
  },

  removePost: function () {
    const self = this
    mp.showModal({
      title: '删除这条心事？',
      content: '删除后不可恢复，也不会再出现在表白墙',
      confirmColor: '#ff4d4f',
      success: function (res) {
        if (!res.confirm) return
        http.del('/wall/posts/' + self.data.id).then(function () {
          toast('已删除')
          mp.setStorageSync('needRefreshWall', 1)
          setTimeout(function () {
            mp.navigateBack()
          }, 600)
        }).catch(function () {})
      }
    })
  },

  goBack: function () {
    mp.navigateBack()
  },

  stop: function () {}
})

function decorate(post) {
  post.timeText = timeAgo(post.createdAt)
  post.avatarClass = 'anon-' + (post.anonAvatar || 1)
  post.avatarEmoji = AVATAR_EMOJI[(post.anonAvatar || 1) - 1] || '💌'
  post.imageList = resolveList(post.imageList)
  // 全市范围下可能刷到外校的心事：与首页/帖子详情同口径，用 schoolId 对比
  const me = (getApp() && getApp().globalData.user) || mp.getStorageSync('user') || {}
  post.sameSchool = !!(me.schoolId && post.schoolId && String(me.schoolId) === String(post.schoolId))
  return post
}
