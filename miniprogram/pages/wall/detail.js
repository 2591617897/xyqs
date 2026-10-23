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
    commentFocus: false,
    showEmoji: false,
    emojiList: EMOJIS,
    sending: false,
    showEdit: false,
    editContent: '',
    editImages: [],
    saving: false
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

  /* ---------- 评论输入 ---------- */
  focusInput: function () {
    this.setData({ commentFocus: true, showEmoji: false })
  },

  onInputBlur: function () {
    this.setData({ commentFocus: false })
  },

  onCommentInput: function (e) {
    this.setData({ commentInput: e.detail.value })
  },

  toggleEmoji: function () {
    this.setData({ showEmoji: !this.data.showEmoji, commentFocus: false })
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
    if (this.data.sending) return
    this.setData({ sending: true })
    if (images.length) mp.showLoading({ title: '上传图片中', mask: true })
    auth.ensureVerified().then(function () {
      return upload.uploadImages(images)
    }).then(function (urls) {
      if (images.length) mp.hideLoading()
      return http.post('/wall/posts/' + self.data.id + '/comments', {
        content: content,
        images: urls
      })
    }).then(function () {
      self.setData({
        commentInput: '',
        commentImages: [],
        showEmoji: false,
        commentFocus: false,
        sending: false
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
  return post
}
