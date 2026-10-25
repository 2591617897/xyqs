const { timeAgo, priceText, fmtCount } = require('./util')
const { resolveList } = require('./img')
const { decorateWall } = require('./wall')

/**
 * 首页「功能区」配置。
 * ------------------------------------------------------------------
 * 为什么首页不再用「频道 tab」：
 *   频道 tab 只能横向排开，频道一多就要折叠 + 横滑，找功能全靠记忆；
 *   功能区是纵向堆叠的模块，一屏一眼看清有哪些功能，加新功能不动导航。
 *
 * 页面结构（自上而下）：
 *   1. 顶部栏（学校范围胶囊 / 搜索 / 通知）
 *   2. 功能入口区（金刚区）—— 一排图标直达各功能，由 buildEntries 生成
 *   3. 运营公告位
 *   4. 内容功能区 —— 每个模块一段单列全宽卡片，由 buildZones 生成
 *
 * 后期加一个新功能的步骤：
 *   1. 在 ZONES 里加一条模块配置（title/icon/desc/more/size）
 *   2. 若是独立功能页，在 ENTRIES 里加一条入口（会出现在金刚区）
 *   3. 在 pages/index/index.wxml 里给它选一个卡片模板
 *      （type=post / wall / goods 三选一，都是单列全宽卡片）
 *   首页的加载、点赞、更多入口逻辑全部自动生效，不用改导航、不用改 tabBar。
 */

/** 公告频道 id：唯一一个「独立成区」的帖子频道 */
const NOTICE_CHANNEL_ID = 2001

/** 校园生活区排除的频道（同 NOTICE_CHANNEL_ID） */
const EXCLUDE_CHANNEL_ID = NOTICE_CHANNEL_ID

/**
 * 每个功能区最多展示几张卡片。
 * 卡片是「单列全宽」，不像以前的横向卡片行一屏能塞两三张，
 * 所以必须收敛条数，否则首页一屏一卡要滑很久；剩下的走各区右上角「更多」。
 */
const ZONE_LIMIT = 3

/** 金刚区固定入口（不含频道，频道入口由后端 t_channel 动态生成） */
const ENTRIES = [
  { key: 'wall', icon: '💌', title: '我想说', url: '/pages/wall/index', tone: 'like' },
  { key: 'goods', icon: '🛍️', title: '校园闲置', url: '/pages/goods/list', tone: 'brand' },
  { key: 'posts', icon: '🗂', title: '校园频道', url: '/pages/post/list', tone: 'info' }
]

/** 金刚区图标底色轮换 */
const TONES = ['brand', 'like', 'info', 'warn', 'violet']

/**
 * 生成金刚区入口列表。
 * 固定入口在前，频道入口在后 —— 后台给 t_channel 加一行，这里自动多一个图标。
 * @param {Array} channels 后端 GET /channels 返回的频道列表
 */
function buildEntries(channels) {
  const list = ENTRIES.map(function (e) {
    return Object.assign({}, e)
  })
  const chs = channels || []
  chs.forEach(function (c) {
    list.push({
      key: 'ch-' + c.id,
      icon: c.icon || '📌',
      title: c.name || '校园频道',
      url: '/pages/post/list?channelId=' + c.id,
      tone: TONES[list.length % TONES.length]
    })
  })
  return list
}

/**
 * 生成首页的内容功能区。
 *
 * 各区的数据来源：
 *   hot    ← GET /posts?sort=hot        （全站点赞第一）
 *   wall   ← GET /wall/posts             （表白墙 = 我想说）
 *   goods  ← GET /goods                  （校园闲置）
 *   posts  ← GET /posts                  （按 channelId 拆成「校园公告」+「校园生活」）
 *
 * 没有任何数据的区直接不渲染（首页不留空模块）。
 *
 * @param {object} src { hot, wall, goods, posts } 均为 { records: [] } 结构
 * @param {Array} channels 频道列表，用于取公告频道的标题与图标
 */
function buildZones(src, channels) {
  const out = []
  const hotRecords = records(src.hot)
  const wallRecords = records(src.wall)
  const goodsRecords = records(src.goods)
  const postRecords = records(src.posts)

  if (hotRecords.length) {
    out.push({
      key: 'hot',
      type: 'hot',
      title: '今日最热',
      icon: '🔥',
      desc: '点赞榜第一',
      more: '',
      items: [postItem(hotRecords[0])]
    })
  }

  if (wallRecords.length) {
    out.push({
      key: 'wall',
      type: 'wall',
      title: '我想说',
      icon: '💌',
      desc: '匿名把心里话说出来',
      more: '/pages/wall/index',
      items: take(wallRecords).map(wallItem)
    })
  }

  if (goodsRecords.length) {
    out.push({
      key: 'goods',
      type: 'goods',
      title: '校园闲置',
      icon: '🛍️',
      desc: '闲置流转，低碳又省钱',
      more: '/pages/goods/list',
      items: take(goodsRecords).map(goodsItem)
    })
  }

  // 公告频道单独成区：失物招领这类信息混在生活帖里会被埋掉
  const noticeChannel = findChannel(channels, NOTICE_CHANNEL_ID)
  const notice = postRecords.filter(function (p) {
    return String(p.channelId) === String(NOTICE_CHANNEL_ID)
  })
  if (notice.length) {
    out.push({
      key: 'notice',
      type: 'post',
      title: (noticeChannel && noticeChannel.name) || '校园公告',
      icon: (noticeChannel && noticeChannel.icon) || '📢',
      desc: (noticeChannel && noticeChannel.description) || '失物招领 · 校园通知',
      more: '/pages/post/list?channelId=' + NOTICE_CHANNEL_ID,
      items: take(notice).map(postItem)
    })
  }

  // 其余频道合成「校园生活」区
  const life = postRecords.filter(function (p) {
    return String(p.channelId) !== String(EXCLUDE_CHANNEL_ID)
  })
  if (life.length) {
    out.push({
      key: 'campus',
      type: 'post',
      title: '校园生活',
      icon: '🎪',
      desc: '吃喝玩乐 · 树洞吐槽 · 运动搭子',
      more: '/pages/post/list',
      items: take(life).map(postItem)
    })
  }

  return out
}

function records(res) {
  return (res && res.records) || []
}

/** 单列全宽卡片，每区只留前 ZONE_LIMIT 条 */
function take(list) {
  return (list || []).slice(0, ZONE_LIMIT)
}

function findChannel(channels, id) {
  const chs = channels || []
  for (let i = 0; i < chs.length; i++) {
    if (String(chs[i].id) === String(id)) return chs[i]
  }
  return null
}

/** 帖子 → 卡片数据 */
function postItem(p) {
  return {
    kind: 'post',
    key: 'post-' + p.id,
    id: p.id,
    authorId: p.userId,
    authorName: p.authorName || '校园用户',
    avatar: p.authorAvatar || '',
    badge: p.channelName || '校园频道',
    timeText: timeAgo(p.createdAt),
    title: p.title || '',
    excerpt: (p.content || '').replace(/\s+/g, ' '),
    imageList: resolveList(p.imageList),
    liked: !!p.liked,
    likeCount: p.likeCount || 0,
    likeText: fmtCount(p.likeCount || 0),
    commentCount: p.commentCount || 0,
    commentText: fmtCount(p.commentCount || 0),
    viewCount: p.viewCount || 0,
    viewText: fmtCount(p.viewCount || 0)
  }
}

/** 表白墙帖子 → 卡片数据 */
function wallItem(p) {
  const w = decorateWall(p)
  return {
    kind: 'wall',
    key: 'wall-' + w.id,
    id: w.id,
    anonName: w.anonName || '匿名同学',
    avatarClass: w.avatarClass,
    avatarEmoji: w.avatarEmoji,
    flair: w.flair,
    tags: w.tags,
    timeText: w.timeText,
    content: w.content || '',
    imageList: w.imageList,
    liked: !!w.liked,
    likeCount: w.likeCount || 0,
    likeText: fmtCount(w.likeCount || 0),
    commentCount: w.commentCount || 0,
    commentText: fmtCount(w.commentCount || 0),
    viewCount: w.viewCount || 0,
    viewText: fmtCount(w.viewCount || 0)
  }
}

/** 闲置 → 卡片数据 */
function goodsItem(g) {
  return {
    kind: 'goods',
    key: 'goods-' + g.id,
    id: g.id,
    authorId: g.userId,
    authorName: g.sellerName || '校园用户',
    badge: g.tradeType === 1 ? '校内自提' : '支持邮寄',
    timeText: timeAgo(g.createdAt),
    title: g.title || '',
    excerpt: (g.description || '').replace(/\s+/g, ' '),
    priceText: priceText(g.price),
    originalPriceText: g.originalPrice ? priceText(g.originalPrice) : '',
    location: g.location || '',
    categoryName: g.categoryName || '',
    imageList: resolveList(g.imageList),
    liked: !!g.liked,
    likeCount: g.likeCount || 0,
    likeText: fmtCount(g.likeCount || 0)
  }
}

module.exports = {
  NOTICE_CHANNEL_ID,
  ENTRIES,
  buildEntries,
  buildZones,
  postItem,
  wallItem,
  goodsItem
}
