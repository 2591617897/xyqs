const http = require('../../../utils/request')
const { mp } = require('../../../utils/util')

const FLAIR_MAP = {
  love: '💗 精选表白',
  tree: '🌳 树洞吐槽',
  find: '🔍 寻物启事',
  findp: '🎣 捞人'
}

Page({
  data: {
    loading: true,
    items: [],
    totalCount: 0,
    postCount: 0,
    wallCount: 0
  },

  onLoad: function () {
    this.load()
  },

  onShow: function () {
    if (this.data.loading === false) this.load()
  },

  load: function () {
    const self = this
    this.setData({ loading: true })

    const user = getApp().globalData.user
    const meId = user && user.id ? user.id : ''
    if (!meId) {
      // 没登录/缓存还没回来：直接出空态，别发出 userId= 的空参数请求
      this.setData({ loading: false, items: [], totalCount: 0, postCount: 0, wallCount: 0 })
      return
    }

    // 注意：request 内部已拼 BASE_URL（本身就带 /api），这里**不能再写 /api 前缀**，
    // 否则会请求到 /api/api/posts 直接 404，结果被 catch 吞成空列表。
    const query = { userId: meId, page: 1, size: 30 }
    const empty = { records: [] }
    Promise.all([
      http.get('/posts', query, { silent: true }).catch(function (err) {
        console.error('[dynamic] 帖子列表失败', err)
        return empty
      }),
      http.get('/wall/posts', query, { silent: true }).catch(function (err) {
        console.error('[dynamic] 表白列表失败', err)
        return empty
      })
    ]).then(function (results) {
      const postData = results[0] || {}
      const wallData = results[1] || {}
      const posts = postData.records || []
      const walls = wallData.records || []

      // 给每条加 _type / _key，wall 还要加 flair
      const decorated = []
      posts.forEach(function (p) {
        decorated.push(Object.assign({}, p, {
          _type: 'post',
          _key: 'p-' + p.id,
          timeText: self.timeText(p.createdAt)
        }))
      })
      walls.forEach(function (w) {
        decorated.push(Object.assign({}, w, {
          _type: 'wall',
          _key: 'w-' + w.id,
          flair: FLAIR_MAP[w.category] || '✨ 动态',
          timeText: self.timeText(w.createdAt)
        }))
      })

      // 合并按 createdAt 降序
      decorated.sort(function (a, b) {
        const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0
        const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0
        return tb - ta
      })

      self.setData({
        items: decorated,
        totalCount: decorated.length,
        postCount: posts.length,
        wallCount: walls.length,
        loading: false
      })
    }).catch(function (err) {
      self.setData({ loading: false })
      console.error('[dynamic] load error', err)
    })
  },

  /** 相对时间：刚刚 / 分钟前 / 小时前 / 日期 */
  timeText: function (iso) {
    if (!iso) return ''
    const now = Date.now()
    const t = new Date(iso.replace(' ', 'T')).getTime()
    const diff = Math.max(0, now - t)
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
    if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
    const d = new Date(t)
    return (d.getMonth() + 1) + '月' + d.getDate() + '日'
  },

  goDetail: function (e) {
    const type = e.currentTarget.dataset.type
    const id = e.currentTarget.dataset.id
    if (type === 'post') {
      mp.navigateTo({ url: '/pages/post/detail?id=' + id })
    } else {
      mp.navigateTo({ url: '/pages/wall/detail?id=' + id })
    }
  }
})
