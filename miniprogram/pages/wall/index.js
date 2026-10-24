const http = require('../../utils/request')
const { timeAgo, mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const scopeMod = require('../../utils/scope')
const upload = require('../../utils/upload')
const { resolveList } = require('../../utils/img')

Page({
  data: {
    posts: [],
    page: 1,
    size: 10,
    hasMore: true,
    total: 0,
    sort: 'new',
    loading: false,
    loadError: false,
    showCompose: false,
    content: '',
    images: [],
    submitting: false,
    // 发布弹层里的分类选择
    composeCat: 'love',
    showCatPicker: false,
    // 顶部分类 chips（对齐设计稿）
    cats: [
      { key: 'love', name: '💗 精选表白', tag: '#精选表白' },
      { key: 'tree', name: '🌳 树洞吐槽', tag: '#树洞吐槽' },
      { key: 'find', name: '🔎 寻物启事', tag: '#寻物启事' },
      { key: 'findp', name: '🙋 捞人', tag: '#捞人' }
    ],
    cat: 'love'
  },

  onLoad: function () {
    mp.setNavigationBarTitle({ title: '匿名表白墙' })
    this.load(true)
  },

  onShow: function () {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 3 })
    }
    // 从详情页返回时同步点赞数 / 评论数 / 编辑结果
    if (mp.getStorageSync('needRefreshWall')) {
      mp.removeStorageSync('needRefreshWall')
      this.load(true)
      return
    }
    if (this.data.posts.length === 0) this.load(true)
  },

  onHide: function () {
    // 兜底：若带着弹层离开页面，恢复被隐藏的 tabBar
    if (this.data.showCompose) {
      this.setData({ showCompose: false })
      this.toggleTabBar(false)
    }
  },

  onPullDownRefresh: function () {
    this.load(true)
  },

  onReachBottom: function () {
    this.load(false)
  },

  load: function (reset) {
    const self = this
    if (this.data.loading) return
    if (!reset && !this.data.hasMore) return
    if (reset) this.setData({ page: 1, hasMore: true })
    this.setData({ loading: true })
    const query = {
      page: this.data.page,
      size: this.data.size,
      sort: this.data.sort
    }
    // 「精选表白」是综合流不过滤；其余分类按 category 精确过滤
    if (this.data.cat && this.data.cat !== 'love') {
      query.category = this.data.cat
    }
    // 浏览范围：本校（默认）/ 本市，与首页共用同一份存储
    const scopeParams = scopeMod.postParams()
    Object.keys(scopeParams).forEach(function (k) {
      query[k] = scopeParams[k]
    })
    http.get('/wall/posts', query, { silent: true })
      .then(function (res) {
        const records = (res.records || []).map(decorate)
        self.setData({
          posts: reset ? records : self.data.posts.concat(records),
          page: self.data.page + 1,
          total: res.total || (reset ? records.length : self.data.total + records.length),
          hasMore: res.hasMore,
          loading: false,
          loadError: false
        })
        mp.stopPullDownRefresh()
      })
      .catch(function () {
        self.setData({ loading: false, loadError: true })
        mp.stopPullDownRefresh()
      })
  },

  switchSort: function (e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.sort) return
    this.setData({ sort: key })
    this.load(true)
  },

  /** 分类 chips：精选表白为综合流，其余走后端 category 过滤 */
  switchCat: function (e) {
    const key = e.currentTarget.dataset.key
    if (key === this.data.cat) return
    this.setData({ cat: key })
    this.load(true)
  },

  /** banner 上的主行动点 */
  goSeason: function () {
    this.openCompose()
  },

  /** 顶部铃铛 → 消息中心 */
  goMessages: function () {
    mp.navigateTo({ url: '/pages/messages/index' })
  },

  /** 阻止分享按钮冒泡 */
  noop: function () {},

  /** 分享单条心事：卡片标题取正文摘要，落点是该心事详情 */
  onShareAppMessage: function (res) {
    const id = res && res.target && res.target.dataset ? res.target.dataset.id : ''
    const post = id
      ? (this.data.posts || []).find(function (p) { return String(p.id) === String(id) })
      : null
    let title = '匿名表白墙 · 校园趣事'
    if (post && post.content) {
      const brief = post.content.replace(/\s+/g, ' ').trim()
      title = brief.length > 24 ? brief.slice(0, 24) + '…' : brief
    }
    return {
      title: title,
      path: id ? '/pages/wall/detail?id=' + id : '/pages/wall/index'
    }
  },

  /* ---------- 浏览 ---------- */
  retry: function () {
    this.load(true)
  },

  goDetail: function (e) {
    mp.navigateTo({ url: '/pages/wall/detail?id=' + e.currentTarget.dataset.id })
  },

  preview: function (e) {
    const index = e.currentTarget.dataset.index
    const post = this.data.posts[index]
    if (!post || !post.imageList.length) return
    mp.previewImage({
      current: e.currentTarget.dataset.url,
      urls: post.imageList
    })
  },

  /* ---------- 点赞（不冒泡到卡片） ---------- */
  toggleLike: function (e) {
    const self = this
    const index = e.currentTarget.dataset.index
    const post = this.data.posts[index]
    if (!post) return
    auth.ensureLogin().then(function () {
      return http.post('/wall/posts/' + post.id + '/like')
    }).then(function (liked) {
      const key = 'posts[' + index + ']'
      const patch = {}
      patch[key + '.liked'] = liked
      patch[key + '.likeCount'] = Math.max(0, (post.likeCount || 0) + (liked ? 1 : -1))
      self.setData(patch)
    }).catch(function () {})
  },

  /* ---------- 发布 ---------- */
  openCompose: function () {
    const self = this
    auth.ensureVerified().then(function () {
      self.setData({ showCompose: true })
      self.toggleTabBar(true)
    }).catch(function () {})
  },

  closeCompose: function () {
    this.setData({ showCompose: false })
    this.toggleTabBar(false)
  },

  /** 弹层需盖住自定义 tabBar：自定义 tabBar 是框架层，页面 z-index 盖不住，只能由组件自己隐藏 */
  toggleTabBar: function (hide) {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ hidden: !!hide })
    }
  },

  onContent: function (e) {
    const value = e.detail.value
    this.setData({ content: value })
    // 输入 # 号唤起分类选择（仅在结尾单独一个 # 时触发）
    if (/#$/.test(value) && !/##$/.test(value)) {
      this.setData({ showCatPicker: true })
    }
  },

  /** 发布弹层：直接点分类 chip */
  chooseCat: function (e) {
    this.setData({ composeCat: e.currentTarget.dataset.key, showCatPicker: false })
  },

  /** # 号唤起的面板：选中后把 # 替换成分类标签写回输入框 */
  pickCatTag: function (e) {
    const cat = this.data.cats.find(function (c) { return c.key === e.currentTarget.dataset.key })
    if (!cat) return
    const content = this.data.content.replace(/#$/, '') + cat.tag + ' '
    this.setData({ content: content, composeCat: cat.key, showCatPicker: false })
  },

  closeCatPicker: function () {
    this.setData({ showCatPicker: false })
  },

  chooseImage: function () {
    const self = this
    const rest = 3 - this.data.images.length
    if (rest <= 0) {
      toast('最多 3 张图片')
      return
    }
    mp.chooseImage({
      count: rest,
      sizeType: ['compressed'],
      success: function (res) {
        self.setData({ images: self.data.images.concat(res.tempFilePaths) })
      }
    })
  },

  removeImage: function (e) {
    const images = this.data.images.slice()
    images.splice(e.currentTarget.dataset.index, 1)
    this.setData({ images: images })
  },

  submitPost: function () {
    const self = this
    if (!this.data.content.trim()) {
      toast('说点什么再发吧')
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })
    if (this.data.images.length) {
      mp.showLoading({ title: '上传图片中', mask: true })
    }
    upload.uploadImages(this.data.images).then(function (urls) {
      if (self.data.images.length) mp.hideLoading()
      return http.post('/wall/posts', {
        content: self.data.content.trim(),
        images: urls,
        category: self.data.composeCat
      }, { loading: '发布中' })
    }).then(function () {
      self.setData({ showCompose: false, content: '', images: [], submitting: false, composeCat: 'love', showCatPicker: false })
      self.toggleTabBar(false)
      toast('已匿名发布')
      self.load(true)
    }).catch(function () {
      self.setData({ submitting: false })
      mp.hideLoading()
    })
  },

  /* ---------- 删除（不冒泡） ---------- */
  deletePost: function (e) {
    const self = this
    const post = this.data.posts[e.currentTarget.dataset.index]
    if (!post) return
    mp.showModal({
      title: '删除这条心事？',
      content: '删除后不可恢复',
      confirmColor: '#ff4d4f',
      success: function (res) {
        if (!res.confirm) return
        http.del('/wall/posts/' + post.id).then(function () {
          toast('已删除')
          self.load(true)
        }).catch(function () {})
      }
    })
  },

  stop: function () {}
})

function decorate(item) {
  item.timeText = timeAgo(item.createdAt)
  item.avatarClass = 'anon-' + (item.anonAvatar || 1)
  item.avatarEmoji = ['🌸', '🌙', '🍀', '⭐', '🐳', '🍑', '🌿', '☁️'][(item.anonAvatar || 1) - 1]
  item.imageList = resolveList(item.imageList)
  // 分类标签：来自发布时选择的分类字段
  const catTag = { love: '#精选表白', tree: '#树洞吐槽', find: '#寻物启事', findp: '#捞人' }
  item.tags = [catTag[item.category] || '#校园日常']
  // 匿名昵称旁的彩色小徽标
  const seed = Number(item.id) || 0
  item.flair = ['接投稿送花', '真心话大冒险', '今日树洞', '沉船打捞'][seed % 4]
  return item
}
