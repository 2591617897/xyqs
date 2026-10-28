const { timeAgo, priceText, fmtCount, conditionText } = require('./util')
const { resolveList, imgGridClass, imgMode } = require('./img')
const { decorateWall } = require('./wall')
const demandUtil = require('./demand')

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
  { key: 'wall', icon: '🌳', title: '树洞闲谈', url: '/pages/wall/index', tone: 'like' },
  { key: 'goods', icon: '♻️', title: '闲置交换', url: '/pages/goods/list', tone: 'brand' },
  { key: 'demand', icon: '🙋', title: '需求广场', url: '/subpackages/demand/list', tone: 'warn' },
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
      title: '树洞闲谈',
      icon: '🌳',
      desc: '匿名说说心里话',
      more: '/pages/wall/index',
      items: take(wallRecords).map(wallItem)
    })
  }

  if (goodsRecords.length) {
    out.push({
      key: 'goods',
      type: 'goods',
      title: '闲置交换',
      icon: '♻️',
      desc: '仅限校内当面自提，不支持线上交易',
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
      // desc 用真实频道名拼，不在代码里写死话题名 ——
      // 否则频道改名或下线后，首页文案会和实际频道对不上
      desc: lifeDesc(channels) || '校园话题交流',
      more: '/pages/post/list',
      items: take(life).map(postItem)
    })
  }

  return out
}

/**
 * 「校园生活」区的副标题：取公告之外的前 3 个在线频道名。
 * @param {Array} channels 后端 GET /channels 返回的频道列表（已过滤下线频道）
 */
function lifeDesc(channels) {
  return (channels || [])
    .filter(function (c) {
      return String(c.id) !== String(NOTICE_CHANNEL_ID) && !!c.name
    })
    .slice(0, 3)
    .map(function (c) {
      return c.name
    })
    .join(' · ')
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
  const images = resolveList(p.imageList)
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
    imageList: images,
    imgGrid: imgGridClass(images.length),
    imgMode: imgMode(images.length),
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
    imgGrid: imgGridClass((w.imageList || []).length),
    imgMode: imgMode((w.imageList || []).length),
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
  const price = Number(g.price || 0)
  const origin = Number(g.originalPrice || 0)
  const likeCount = g.likeCount || 0
  const commentCount = g.commentCount || 0
  const viewCount = g.viewCount || 0
  const cond = conditionText(g.conditionLevel)

  // 卖点行：位置 / 分类 / 成色 / 浏览量 —— 全部是卖家自己填的真实属性。
  // 不写「全网热销 20 万+件」「7 天无理由退货」「包邮」这类电商话术：
  // 我们没有这些数据，写了就是虚假宣传，审核和用户两头都不落好。
  const sellParts = []
  if (g.location) sellParts.push({ k: 'loc', v: '📍 ' + g.location, cls: 'sell-loc' })
  if (g.categoryName) sellParts.push({ k: 'cat', v: '🔥 ' + g.categoryName })
  if (cond) sellParts.push({ k: 'cond', v: cond })
  sellParts.push({ k: 'view', v: '👀 ' + fmtCount(viewCount) + ' 浏览' })

  // 特色条（对应参考图的「券 · 已补 X 元」条）：左边说多少人想要，右边说什么时候发布。
  // 想要数为 0 时不硬凑「0 人想要」，退到留言数，再没有就说在校在售。
  let featureMain = '💗 ' + fmtCount(likeCount) + ' 人想要'
  if (!likeCount) {
    featureMain = commentCount > 0
      ? '💬 ' + fmtCount(commentCount) + ' 条留言'
      : '✨ 校内在售'
  }

  return {
    kind: 'goods',
    key: 'goods-' + g.id,
    id: g.id,
    authorId: g.userId,
    authorName: g.sellerName || '校园用户',
    badge: '校内面交',
    timeText: timeAgo(g.createdAt),
    title: g.title || '',
    excerpt: (g.description || '').replace(/\s+/g, ' ').trim(),
    priceText: priceText(g.price),
    originalPriceText: origin ? priceText(g.originalPrice) : '',
    // 原价必须确实高于现价才划线显示，否则「原价 = 现价」纯属噪音
    showOrigin: origin > price && price > 0,
    location: g.location || '',
    categoryName: g.categoryName || '',
    conditionText: cond,
    imageList: resolveList(g.imageList),
    liked: !!g.liked,
    likeCount: likeCount,
    likeText: fmtCount(likeCount),
    viewCount: viewCount,
    viewText: fmtCount(viewCount),
    commentCount: commentCount,
    commentText: fmtCount(commentCount),
    // 新版排版用：卖点行分段 + 特色条左右两段
    sellParts: sellParts,
    featureMain: featureMain,
    featureSub: timeAgo(g.createdAt)
  }
}

/* ================= 校园广场（混合时间流） =================
 *
 * 首页原来是一堆分区：树洞闲谈 / 闲置交换 / 校园公告 / 校园生活，
 * 每区各取 3 条。问题是每区都薄——4 条树洞、3 条帖子、几条闲置，
 * 摆成四块反而把首屏切碎，用户要滑四段才看完。
 * 现在合并成一条「全部」时间流 + 顶部分类 tab，一屏看到最活跃的内容。
 *
 * 两条排序规矩（用户明确要求）：
 *   1. 默认（全部）按发布时间倒序
 *   2. 推广帖不参与时间排序 —— 单独置顶排在最前
 * 推广位只对帖子存在（t_post.promote_score），树洞 / 闲置 / 需求都进 normal 组。
 */

/** 广场固定 tab：对应四大内容模块，后面拼接后端频道 */
const PLAZA_FIXED_TABS = [
  { key: 'all', name: '全部', more: '/pages/post/list' },
  { key: 'wall', name: '树洞', more: '/pages/wall/index' },
  { key: 'goods', name: '闲置', more: '/pages/goods/list' },
  { key: 'demand', name: '需求', more: '/subpackages/demand/list' }
]

/** 广场首屏条数：比原来的「每区 3 条」多一些，因为只剩这一块内容区了 */
const PLAZA_LIMIT = 8

/**
 * 「全部」流里每类的配额。
 *
 * <p>必须限配额：纯按时间倒序取前 8 条时，只要闲置和树洞发得勤，
 * 需求、帖子就会一条都进不了首屏（实测 8 个名额全被 goods/wall 吃掉），
 * 用户在首页永远看不到「需求广场」这个功能，等于白做。
 * 4 类 × 2 条 = 8，正好铺满首屏，谁的发布时间新谁排在前面。
 */
const PLAZA_KIND_QUOTA = 2

/**
 * 按「每类最多 quota 条」挑够 limit 条，结果仍按时间倒序。
 * 第一轮守配额；某类条数不够时会空出名额，第二轮按时间把剩下的补齐。
 */
function mixByQuota(list, limit, quota) {
  if (limit <= 0) return []
  const picked = []
  const rest = []
  const count = {}
  list.forEach(function (it) {
    const n = count[it.kind] || 0
    if (n < quota && picked.length < limit) {
      picked.push(it)
      count[it.kind] = n + 1
    } else {
      rest.push(it)
    }
  })
  for (let i = 0; i < rest.length && picked.length < limit; i++) picked.push(rest[i])
  // 第二轮补进来的条目时间上可能更早，整体重排一次才谈得上「时间倒序」
  return picked.sort(function (a, b) {
    return b.ts - a.ts
  })
}

/**
 * 安全时间戳。
 * iOS 上 `new Date('2026-10-28T10:00:00')` 是 Invalid Date —— 带 `-` 与 `T` 的写法
 * 不在 ECMAScript 规定格式里，必须换成斜杠 + 空格。拿不到就返回 0（沉底），
 * 绝不让一条脏数据把整段顺序搞乱。
 */
function toTs(v) {
  if (!v) return 0
  if (typeof v === 'number') return v
  const s = String(v).trim()
  if (!s) return 0
  const t = new Date(s.replace(/-/g, '/').replace('T', ' ').replace(/\.\d+$/, '')).getTime()
  return isNaN(t) ? 0 : t
}

/** 推广帖：有分数且没过期。过期后残留的 promoteScore 不再享优先位 */
function isPromoted(p) {
  if (!p || !(Number(p.promoteScore) > 0)) return false
  if (!p.promoteExpireAt) return true
  const end = toTs(p.promoteExpireAt)
  return !end || end > Date.now()
}

/** 需求 → 卡片数据（字段口径与 subpackages/demand/list 完全一致） */
function demandItem(d) {
  const it = demandUtil.decorate(Object.assign({}, d))
  return {
    kind: 'demand',
    key: 'demand-' + it.id,
    id: it.id,
    authorId: it.userId,
    authorName: it.authorName || '校园用户',
    avatar: it.authorAvatar || '',
    typeLabel: it.typeLabel,
    typeIcon: it.typeIcon,
    badge: it.categoryName || it.typeLabel,
    title: it.title || '',
    excerpt: it.descText || '',
    budgetText: it.budgetText,
    expectTime: it.expectTime || '',
    location: it.location || '',
    replyCount: it.replyCount || 0,
    replyText: it.replyText,
    imageList: it.imageList || [],
    imgGrid: imgGridClass((it.imageList || []).length),
    imgMode: imgMode((it.imageList || []).length),
    timeText: it.timeText,
    viewCount: it.viewCount || 0,
    viewText: fmtCount(it.viewCount || 0),
    // 需求没有点赞，操作条只显示回应数；给默认值避免 wxml 里判 undefined
    liked: false,
    likeCount: 0,
    likeText: '',
    commentCount: 0,
    commentText: ''
  }
}

/** 给卡片补上原始时间与推广标记（不动三个既有构造函数，其它调用处零影响） */
function stamped(item, raw, promoted) {
  item.ts = toTs(raw && raw.createdAt)
  item.promoted = !!promoted
  return item
}

/**
 * 生成广场：tab 列表 + 当前 tab 的卡片流。
 *
 * @param {object} src          { posts, wall, goods, demands }
 * @param {Array}  channels     后端 GET /channels 在线频道
 * @param {string} tabKey       当前选中 tab（all / wall / goods / demand / ch-<id>）
 * @param {Array}  channelPosts 频道 tab 的数据（切换时才拉，见 pages/index#switchPlazaTab）
 * @returns {{tabs: Array, activeTab: string, items: Array, more: string, empty: boolean}}
 */
function buildPlaza(src, channels, tabKey, channelPosts) {
  src = src || {}
  const key = tabKey || 'all'

  // 固定 tab + 动态频道 tab：后台加一个频道，首页自动多一个分类，不用改代码
  const tabs = PLAZA_FIXED_TABS.map(function (t) {
    return Object.assign({}, t)
  })
  ;(channels || []).forEach(function (c) {
    tabs.push({
      key: 'ch-' + c.id,
      name: c.name || '校园频道',
      more: '/pages/post/list?channelId=' + c.id
    })
  })

  let items = []
  if (key === 'wall') {
    items = records(src.wall).map(function (w) {
      return stamped(wallItem(w), w)
    })
  } else if (key === 'goods') {
    items = records(src.goods).map(function (g) {
      return stamped(goodsItem(g), g)
    })
  } else if (key === 'demand') {
    items = records(src.demands).map(function (d) {
      return stamped(demandItem(d), d)
    })
  } else if (key.indexOf('ch-') === 0) {
    // 频道 tab：数据由调用方单独拉好传进来，这里只做卡片加工
    items = (channelPosts || []).map(function (p) {
      return stamped(postItem(p), p, isPromoted(p))
    })
  } else {
    items = []
      .concat(records(src.posts).map(function (p) {
        return stamped(postItem(p), p, isPromoted(p))
      }))
      .concat(records(src.wall).map(function (w) {
        return stamped(wallItem(w), w)
      }))
      .concat(records(src.goods).map(function (g) {
        return stamped(goodsItem(g), g)
      }))
      .concat(records(src.demands).map(function (d) {
        return stamped(demandItem(d), d)
      }))
  }

  // 推广帖置顶（不参与时间排序），其余按发布时间倒序
  const promoted = []
  const normal = []
  items.forEach(function (it) {
    if (it.promoted) promoted.push(it)
    else normal.push(it)
  })
  const byTime = function (a, b) {
    return b.ts - a.ts
  }

  if (key === 'all') {
    // 推广位最多占一半：一次投很多条时不能整屏都是推广，否则用户直接滑走
    const promoMax = Math.min(promoted.length, Math.max(1, Math.floor(PLAZA_LIMIT / 2)))
    const pickedPromo = promoted.sort(byTime).slice(0, promoMax)
    items = pickedPromo.concat(mixByQuota(normal.sort(byTime), PLAZA_LIMIT - pickedPromo.length, PLAZA_KIND_QUOTA))
  } else {
    // 单类 tab 不需要配额：配额是防止某一类挤掉另一类，这里本来就只有一类
    items = promoted.sort(byTime).concat(normal.sort(byTime)).slice(0, PLAZA_LIMIT)
  }

  const active = tabs.filter(function (t) {
    return t.key === key
  })[0]

  return {
    tabs: tabs,
    activeTab: key,
    items: items,
    more: (active && active.more) || '/pages/post/list',
    empty: !items.length
  }
}

module.exports = {
  imgGridClass,
  NOTICE_CHANNEL_ID,
  ENTRIES,
  PLAZA_FIXED_TABS,
  PLAZA_LIMIT,
  buildEntries,
  buildZones,
  buildPlaza,
  toTs,
  postItem,
  wallItem,
  goodsItem,
  demandItem
}
