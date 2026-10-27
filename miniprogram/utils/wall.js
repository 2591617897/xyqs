const { timeAgo } = require('./util')
const { resolveList } = require('./img')

/**
 * 表白墙（t_wall_post）派生字段的公共算法。
 *
 * 为什么抽出来：首页的「我想说」功能区和表白墙页渲染的是同一批数据，
 * 匿名头像色块、分类话题标签、彩色徽标这些派生字段只在这里算一次，
 * 避免两个页面各写一份、以后改一处漏一处。
 */

/**
 * 分类 → 话题标签（发布时选的 category 字段）。
 * 注意 findp 只用于兼容存量数据：前端已把「寻物」「寻人」合并成一个「求助」tab，
 * 新发布的内容统一落到 find。
 */
const CAT_TAG = {
  love: '#校园心情',
  tree: '#树洞吐槽',
  find: '#求助',
  findp: '#寻人互助'
}

/**
 * 树洞分类的唯一定义源（分类 chips / 发布选择 / 滑动切换都读这一份）。
 *
 * 整理原则：树洞只回答「我现在什么心情 / 需要什么帮忙」，
 * 所以分类按情绪与诉求切，不按物品切（物品归闲置）、不按公共话题切（话题归频道）。
 *
 * - key 为空 = 不过滤，对应「全部」
 * - query 非空 = 实际发给后端 category 参数的值（「求助」= 寻物 + 寻人）
 * - 后端发布接口只收单值，「求助」发布时统一写 find（见后端 normalizeCategory）
 */
const CATS = [
  { key: '', name: '全部', tag: '', query: '' },
  { key: 'love', name: '💗 心情', tag: CAT_TAG.love, query: 'love' },
  { key: 'tree', name: '🕳️ 吐槽', tag: CAT_TAG.tree, query: 'tree' },
  { key: 'find', name: '🔎 求助', tag: CAT_TAG.find, query: 'find,findp' }
]

/** 发布弹层可选的分类：去掉「全部」（发布必须落到一个具体分类） */
const COMPOSE_CATS = CATS.filter(function (c) {
  return !!c.key
})

/** 按 key 找分类，找不到返回第一个 */
function findCat(key) {
  for (let i = 0; i < CATS.length; i++) {
    if (CATS[i].key === key) return CATS[i]
  }
  return CATS[0]
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
  CATS,
  COMPOSE_CATS,
  findCat,
  AVATAR_EMOJI,
  FLAIRS,
  decorateWall
}
