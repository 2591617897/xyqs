/**
 * 官方通知（消息中心四宫格 → 独立子页面）
 * 对应通知类型：SYSTEM（单 type，交给后端过滤，分页与总数都准）
 */
const { createNoticePage } = require('../common/notice')

Page(createNoticePage({
  key: 'system',
  title: '官方通知',
  types: ['SYSTEM'],
  emptyIcon: '📣',
  emptyText: '平台公告、活动与安全提醒会出现在这里',
  unreadPick: function (u) { return u.system || 0 }
}))
