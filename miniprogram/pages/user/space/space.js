const http = require('../../../utils/request')
const { timeAgo, priceText, mp, toast } = require('../../../utils/util')
const { resolve, resolveList } = require('../../../utils/img')
const followMod = require('../../../utils/follow')

/** 每页条数（触底分页） */
const PAGE_SIZE = 10
/** 闲置状态文案，与后端 t_goods.status 对齐 */
const GOODS_STATUS = { 0: '已下架', 1: '在售中', 2: '已售出' }
const GOODS_STATUS_CLASS = { 0: 'off', 1: 'sale', 2: 'sold' }

Page({
  data: {
    userId: '',
    profile: null,
    following: false,
    // post | goods，默认帖子
    tab: 'post',
    posts: [],
    goods: [],
    postsLoading: true,
    goodsLoading: true,
    postsDone: false,
    goodsDone: false,
    followLoading: false,
    notFound: false
  },

  onLoad: function (options) {
    // 雪花 ID 全程字符串透传，禁止 Number()
    this.userId = options.id ? String(options.id) : ''
    this.postsPage = 1
    this.goodsPage = 1
    this.goodsLoaded = false
    this.loadProfile()
    this.loadPosts(true)
  },

  onPullDownRefresh: function () {
    this.loadProfile()
    if (this.data.tab === 'goods') {
      this.loadGoods(true)
    } else {
      this.loadPosts(true)
    }
  },

  onReachBottom: function () {
    if (this.data.tab === 'goods') {
      this.loadGoods(false)
    } else {
      this.loadPosts(false)
    }
  },

  /* ---------- 公开主页 ---------- */

  loadProfile: function () {
    const self = this
    if (!this.userId) {
      self.setData({ notFound: true, postsLoading: false, goodsLoading: false })
      return
    }
    http.get('/user/' + this.userId + '/profile', null, { silent: true }).then(function (p) {
      if (!p) return
      // 头像走 img.resolve 统一寻址（相对路径 / 历史绝对地址都收敛到当前服务器）
      p.avatar = resolve(p.avatar)
      self.setData({
        profile: p,
        following: !!p.following,
        notFound: false
      })
    }).catch(function (err) {
      // 404：用户不存在或已被封禁
      if (err && err.code === 404) {
        self.setData({ notFound: true, postsLoading: false, goodsLoading: false })
      }
    })
  },

  /* ---------- 内容列表（按作者过滤，触底分页） ---------- */

  loadPosts: function (refresh) {
    const self = this
    if (refresh) {
      this.postsPage = 1
      this.setData({ postsLoading: true, postsDone: false })
    } else {
      if (this.data.postsDone || this.data.postsLoadingMore) return
      this.setData({ postsLoadingMore: true })
    }
    http.get('/posts', { userId: this.userId, page: this.postsPage, size: PAGE_SIZE }, { silent: true })
      .then(function (res) {
        const list = ((res && res.records) || []).map(mapPost)
        self.setData({
          posts: refresh ? list : self.data.posts.concat(list),
          postsLoading: false,
          postsLoadingMore: false,
          postsDone: !res || !res.hasMore
        })
        self.postsPage += 1
      }).catch(function () {
        self.setData({ postsLoading: false, postsLoadingMore: false })
      })
  },

  loadGoods: function (refresh) {
    const self = this
    if (refresh) {
      this.goodsPage = 1
      this.setData({ goodsLoading: true, goodsDone: false })
    } else {
      if (this.data.goodsDone || this.data.goodsLoadingMore) return
      this.setData({ goodsLoadingMore: true })
    }
    // 不传 status：后端返回该作者全部状态（0下架/1在售/2已售出），前端渲染状态标签
    http.get('/goods', { userId: this.userId, page: this.goodsPage, size: PAGE_SIZE }, { silent: true })
      .then(function (res) {
        const list = ((res && res.records) || []).map(mapGoods)
        self.setData({
          goods: refresh ? list : self.data.goods.concat(list),
          goodsLoading: false,
          goodsLoadingMore: false,
          goodsDone: !res || !res.hasMore
        })
        self.goodsPage += 1
      }).catch(function () {
        self.setData({ goodsLoading: false, goodsLoadingMore: false })
      })
  },

  switchTab: function (e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.tab) return
    this.setData({ tab: tab })
    // 首次切到闲置才懒加载，避免进页面两列齐发
    if (tab === 'goods' && !this.goodsLoaded) {
      this.goodsLoaded = true
      this.loadGoods(true)
    }
  },

  /* ---------- 关注（乐观更新 + 防重复点击 + 失败回滚） ---------- */

  onFollowTap: function () {
    if (this.data.followLoading) return
    const self = this
    const profile = this.data.profile || {}
    const prev = { following: this.data.following, fansCount: profile.fansCount || 0 }
    const next = !prev.following
    this.setData({
      followLoading: true,
      following: next,
      'profile.fansCount': Math.max(0, prev.fansCount + (next ? 1 : -1))
    })
    followMod.toggle(this.userId, prev.following).then(function (res) {
      // 以服务端返回校正状态与粉丝数
      self.setData({
        followLoading: false,
        following: !!res.following,
        'profile.fansCount': res.fansCount == null ? prev.fansCount : res.fansCount
      })
      toast(next ? '已关注' : '已取消关注')
    }).catch(function () {
      // 回滚到操作前状态
      self.setData({
        followLoading: false,
        following: prev.following,
        'profile.fansCount': prev.fansCount
      })
      toast('操作失败，请重试')
    })
  },

  /* ---------- 跳转 ---------- */

  goPostDetail: function (e) {
    mp.navigateTo({ url: '/pages/post/detail?id=' + e.currentTarget.dataset.id })
  },

  goGoodsDetail: function (e) {
    mp.navigateTo({ url: '/pages/goods/detail?id=' + e.currentTarget.dataset.id })
  },

  /** 本人主页：「编辑资料」→ 个人信息页 */
  goEditProfile: function () {
    mp.navigateTo({ url: '/pages/user/profile' })
  }
})

/** 帖子卡字段映射，与首页信息流保持一致 */
function mapPost(p) {
  return {
    type: 'post',
    key: 'post-' + p.id,
    id: p.id,
    authorName: p.authorName || '校园用户',
    avatar: p.authorAvatar || '',
    timeText: timeAgo(p.createdAt),
    badge: p.channelName || '校园频道',
    title: p.title || '',
    excerpt: (p.content || '').replace(/\s+/g, ' '),
    imageList: resolveList(p.imageList),
    likeCount: p.likeCount || 0,
    likeText: fmtCount(p.likeCount),
    commentCount: p.commentCount || 0,
    commentText: fmtCount(p.commentCount)
  }
}

/** 闲置卡字段映射：价格 / 状态标签 / 封面 */
function mapGoods(g) {
  return {
    type: 'goods',
    key: 'goods-' + g.id,
    id: g.id,
    title: g.title || '',
    priceText: priceText(g.price),
    imageList: resolveList(g.imageList),
    status: g.status == null ? 1 : g.status,
    statusText: GOODS_STATUS[g.status == null ? 1 : g.status] || '在售中',
    statusClass: GOODS_STATUS_CLASS[g.status == null ? 1 : g.status] || 'sale',
    location: g.location || '',
    timeText: timeAgo(g.createdAt),
    viewCount: g.viewCount || 0,
    viewText: fmtCount(g.viewCount)
  }
}

/** 计数美化：1200 → 1.2k，12345 → 1.2万（与首页一致） */
function fmtCount(n) {
  n = Number(n) || 0
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + '万'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}
