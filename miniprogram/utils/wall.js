const { timeAgo } = require('./util')
const { resolveList } = require('./img')

/**
 * 表白墙（t_wall_post）派生字段的公共算法。
 *
 * 为什么抽出来：首页的「我想说」功能区和表白墙页渲染的是同一批数据，
 * 匿名头像色块、分类话题标签、彩色徽标这些派生字段只在这里算一次，
 * 避免两个页面各写一份、以后改一处漏一处。
 */

/** 分类 → 话题标签（发布时选的 category 字段） */
const CAT_TAG = {
  love: '#精选表白',
  tree: '#树洞吐槽',
  find: '#寻物启事',
  findp: '#捞人'
}

/** 匿名头像 emoji（anonAvatar 1~8 对应） */
const AVATAR_EMOJI = ['🌸', '🌙', '🍀', '⭐', '🐳', '🍑', '🌿', '☁️']

/** 匿名昵称旁的彩色小徽标 */
const FLAIRS = ['接投稿送花', '真心话大冒险', '今日树洞', '沉船打捞']

/**
 * 给一条表白墙帖子补上派生字段（原地修改并返回）。
 * @param {object} item 后端 / 模拟层返回的原始帖子
 */
function decorateWall(item) {
  item.timeText = timeAgo(item.createdAt)
  // 只取色号类（.anon-N），尺寸交给各页面自己的类，避免和 app.wxss 的 .anon-avatar 打架
  item.avatarClass = 'anon-' + (item.anonAvatar || 1)
  item.avatarEmoji = AVATAR_EMOJI[(item.anonAvatar || 1) - 1]
  item.imageList = resolveList(item.imageList)
  item.tags = [CAT_TAG[item.category] || '#校园日常']
  const seed = Number(item.id) || 0
  item.flair = FLAIRS[seed % 4]
  return item
}

module.exports = {
  CAT_TAG,
  AVATAR_EMOJI,
  FLAIRS,
  decorateWall
}
