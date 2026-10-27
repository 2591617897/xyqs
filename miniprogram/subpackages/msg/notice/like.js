/**
 * 赞与收藏（消息中心四宫格 → 独立子页面）
 * 对应通知类型：LIKE_GOODS / LIKE_POST / LIKE_WALL
 */
const { createNoticePage } = require('../common/notice')

Page(createNoticePage({
  key: 'like',
  title: '赞与收藏',
  types: ['LIKE_GOODS', 'LIKE_POST', 'LIKE_WALL'],
  emptyIcon: '💗',
  emptyText: '还没人给你点赞，发条动态试试',
  unreadPick: function (u) { return u.like || 0 }
}))
