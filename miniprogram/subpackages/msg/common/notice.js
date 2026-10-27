/**
 * 通知列表页公共逻辑（subpackages/msg/notice 下四个子页面共用）
 *
 * 为什么抽出来：赞与收藏 / 新增粉丝 / 评论与@ / 官方通知 这四页除了
 * 「后端 type 集合」和「空态文案」完全一样，抄四份等于以后改一次要改四处。
 * 每个页面 js 只有 6~8 行，配置 + Page(createNoticePage(cfg)) 即可。
 *
 * 数据来源（campus-message）：
 *   GET  /messages/notifications?type=&page=&size=   分页列表
 *   GET  /messages/notifications/unread              各类未读数
 *   POST /messages/notifications/read/{type}         按类标记已读
 */
const http = require('../../../utils/request')
const { mp, timeAgo, toast } = require('../../../utils/util')
const auth = require('../../../utils/auth')

/** 通知类型 → 一句话动作文案（与后端 type 枚举一一对应） */
const ACTION_TEXT = {
  LIKE_GOODS: '给你的闲置点了个赞',
  LIKE_POST: '给你的动态点了个赞',
  LIKE_WALL: '给你的树洞点了个赞',
  NEW_FOLLOWER: '关注了你',
  COMMENT_GOODS: '评论了你的闲置',
  COMMENT_POST: '评论了你的动态',
  COMMENT_WALL: '评论了你的树洞',
  MENTION: '@了你',
  SYSTEM: '发来一条消息'
}

/**
 * 单条通知加工：id 转字符串（雪花 id 19 位，Number() 会掉精度）、
 * unread 布尔化、时间文案、动作文案。
 */
function decorate(n) {
  n.id = String(n.id)
  n.unread = Number(n.isRead) === 0
  n.timeText = shortTime(n.createdAt)
  n.actionText = ACTION_TEXT[n.type] || '发来一条通知'
  n.actorName = n.actorName || '校聚一城'
  return n
}

/**
 * @param {Object} cfg
 * @param {String}   cfg.key        页面标识（like / fans / comment / system）
 * @param {String}   cfg.title      顶部标题
 * @param {String[]} cfg.types      后端 type 集合
 * @param {String}   cfg.emptyIcon  空态图标
 * @param {String}   cfg.emptyText  空态文案
 * @param {Function} cfg.unreadPick 从 /notifications/unread 结果里取本页未读数
 */
function createNoticePage(cfg) {
  const types = (cfg.types || []).slice()
  // 后端 list 只支持单个 type（没有 OR 查询）：
  // 单一 type 直接交给服务端过滤，分页和总数都准确；
  // 多 type（赞类 3 种 / 评论与@ 5 种）只能拉全量后在本地过滤。
  const serverFilter = types.length === 1
  const pickUnread = cfg.unreadPick || function () { return 0 }

  return {
    data: {
      key: cfg.key,
      title: cfg.title,
      emptyIcon: cfg.emptyIcon || '✨',
      emptyText: cfg.emptyText || '这里还什么都没有',
      items: [],
      /** 已拉到的后端页码 */
      page: 0,
      size: 20,
      loading: true,
      loadingMore: false,
      noMore: false,
      unreadCount: 0,
      /** 游客：/messages/notifications 需要登录，先给登录引导而不是打一堆 401 */
      needLogin: false
    },

    /**
     * 首屏不在 onLoad 拉：tab 页返回、从详情页返回都要刷新已读状态。
     * onShow 首次置 _loaded，之后只刷未读数（不动列表，避免滚动位置被重置）。
     */
    onShow: function () {
      if (!this._loaded) {
        this._loaded = true
        this.reload()
      } else {
        this.refreshUnread()
      }
    },

    onPullDownRefresh: function () {
      this.reload(function () {
        mp.stopPullDownRefresh()
      })
    },

    onReachBottom: function () {
      if (this.data.loading || this.data.loadingMore || this.data.noMore) return
      if (this.data.needLogin) return
      this.fetchPage(this.data.page + 1)
    },

    /** 重置到第一页 */
    reload: function (done) {
      if (!http.getToken()) {
        this.setData({ loading: false, needLogin: true, items: [], unreadCount: 0 })
        if (done) done()
        return
      }
      this.setData({ loading: true, needLogin: false, noMore: false, loadingMore: false })
      this.refreshUnread()
      this.fetchPage(1, done)
    },

    /**
     * 拉一页。
     * 多 type 本地过滤时可能出现「这一页过滤后是空的但后端还有」的情况，
     * 这里自动顺延下一页（最多连跳 5 页，防止极端数据把请求打爆）。
     */
    fetchPage: function (page, done, hop) {
      const self = this
      const params = { page: page, size: this.data.size }
      if (serverFilter) params.type = types[0]
      if (page > 1) this.setData({ loadingMore: true })

      http.get('/messages/notifications', params, { silent: true }).then(function (res) {
        const records = (res && res.records) || []
        const total = Number((res && res.total) || 0)
        const list = []
        records.forEach(function (n) {
          // 后端已按 type 过滤时这里恒成立；多 type 时在本地筛
          if (types.indexOf(n.type) < 0) return
          list.push(decorate(n))
        })
        // 后端是否还有下一页：有 total 看 total，没有就看这一页够不够 size
        const noMore = records.length < self.data.size ||
          (total > 0 && page * self.data.size >= total)

        if (!list.length && !noMore && page > 1 && (hop || 0) < 5) {
          // 本页全被过滤掉了，继续顺延
          self.fetchPage(page + 1, done, (hop || 0) + 1)
          return
        }

        const patch = { loading: false, loadingMore: false, page: page, noMore: noMore }
        if (page === 1) {
          patch.items = list
        } else {
          // 合并去重：评论与@ 这类混排时同一条可能被两个 type 命中
          const seen = {}
          const merged = []
          self.data.items.concat(list).forEach(function (n) {
            if (seen[n.id]) return
            seen[n.id] = 1
            merged.push(n)
          })
          patch.items = merged
        }
        self.setData(patch)
        self.refreshUnread()
        if (done) done()
      }).catch(function () {
        self.setData({ loading: false, loadingMore: false })
        if (page === 1) self.setData({ items: [] })
        if (done) done()
      })
    },

    /** 未读数：local 也够用，但服务端口径更准（含未加载的历史消息） */
    refreshUnread: function () {
      const self = this
      if (!http.getToken()) return
      http.get('/messages/notifications/unread', null, { silent: true }).then(function (u) {
        self.setData({ unreadCount: Number(pickUnread(u || {})) || 0 })
      }).catch(function () {})
    },

    /** 全部标为已读：按 type 逐个标（后端只有单 type 接口） */
    onMarkAllRead: function () {
      const self = this
      if (!this.data.items.length) { toast('暂时没有通知'); return }
      if (!this.data.unreadCount) { toast('已经全部读过了'); return }
      Promise.all(types.map(function (t) {
        return http.post('/messages/notifications/read/' + t, {}, { silent: true })
          .catch(function () { return 0 })
      })).then(function () {
        const patch = { unreadCount: 0 }
        self.data.items.forEach(function (n, i) {
          if (n.unread) patch['items[' + i + '].unread'] = false
        })
        self.setData(patch)
        toast('已全部标为已读')
      })
    },

    /**
     * 点卡片：先乐观标记已读（后端异步补刀），再按 targetType 跳详情。
     * 官方通知没有 targetType，只弹摘要，不乱跳。
     */
    onTapItem: function (e) {
      const idx = Number(e.currentTarget.dataset.index)
      const item = this.data.items[idx]
      if (!item) return

      if (item.unread) {
        const patch = {}
        patch['items[' + idx + '].unread'] = false
        patch.unreadCount = Math.max(0, this.data.unreadCount - 1)
        this.setData(patch)
        http.post('/messages/notifications/read/' + item.type, {}, { silent: true }).catch(function () {})
      }

      // 雪花 id 全程 String()，任何一处 Number() 都会把 …9601 变成 …9600
      const targetId = item.targetId == null ? '' : String(item.targetId)
      const actorId = item.actorId == null ? '' : String(item.actorId)
      let url = ''
      switch (item.targetType) {
        case 'GOODS': if (targetId) url = '/pages/goods/detail?id=' + targetId; break
        case 'POST': if (targetId) url = '/pages/post/detail?id=' + targetId; break
        case 'WALL': if (targetId) url = '/pages/wall/detail?id=' + targetId; break
        case 'FOCUS': if (actorId) url = '/pages/user/space/space?id=' + actorId; break
        default: break
      }
      if (url) {
        mp.navigateTo({ url: url })
      } else {
        toast(item.contentSnapshot || '这条通知没有可跳转的内容')
      }
    },

    /** 游客态：点「去登录」才真正建号（拒绝则继续留在空态） */
    goLogin: function () {
      const self = this
      auth.ensureLogin(true).then(function () {
        self.reload()
      }).catch(function () {})
    }
  }
}

/** 通知时间：今天显示 HH:MM，昨天带「昨天」，更早用相对时间 */
function shortTime(t) {
  if (!t) return ''
  const d = new Date(String(t).replace(/-/g, '/'))
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return pad2(d.getHours()) + ':' + pad2(d.getMinutes())
  }
  const yesterday = new Date(now.getTime() - 86400000)
  if (d.toDateString() === yesterday.toDateString()) {
    return '昨天 ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes())
  }
  return timeAgo(t)
}

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

module.exports = {
  createNoticePage: createNoticePage,
  ACTION_TEXT: ACTION_TEXT
}
