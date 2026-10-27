/**
 * 评论与@（消息中心四宫格 → 独立子页面）
 * 对应通知类型：COMMENT_GOODS / COMMENT_POST / COMMENT_WALL / MENTION
 * 注意后端没有 COMMENT_GOODS 之外的合并查询，本页是多 type → 拉全量本地过滤
 */
const { createNoticePage } = require('../common/notice')

Page(createNoticePage({
  key: 'comment',
  title: '评论与@',
  types: ['COMMENT_GOODS', 'COMMENT_POST', 'COMMENT_WALL', 'MENTION'],
  emptyIcon: '💬',
  emptyText: '还没人评论或 @ 你，去逛逛校园圈',
  // 未读口径和消息页 badge 保持一致：帖子评论 + 树洞评论 + @
  unreadPick: function (u) {
    return (u.commentPost || 0) + (u.commentWall || 0) + (u.mention || 0)
  }
}))
