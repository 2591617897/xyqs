/**
 * 新增粉丝（消息中心四宫格 → 独立子页面）
 * 对应通知类型：NEW_FOLLOWER
 */
const { createNoticePage } = require('../common/notice')

Page(createNoticePage({
  key: 'fans',
  title: '新增粉丝',
  types: ['NEW_FOLLOWER'],
  emptyIcon: '👤',
  emptyText: '多互动几次，就会有人关注你啦',
  unreadPick: function (u) { return u.follow || 0 }
}))
