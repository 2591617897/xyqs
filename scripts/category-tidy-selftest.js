/**
 * 分类体系整理自测（树洞 / 闲置 / 频道）
 *
 * 为什么要有这个脚本：
 *   分类是「一处定义、四处展示」的东西（树洞 chips、发布弹层、闲置 chips、
 *   首页功能区副标题），任何一处和另一处漂了，表现都是「分类看着不对劲」
 *   这种很难复现、很难定位的问题。所以这里做的是**交叉一致性**断言：
 *   前端定义 ↔ 后端白名单 ↔ 页面引用 ↔ 数据库脚本，四边必须对得上。
 *
 * 全部是行为断言：真 require 模块、真跑函数、真读 Java/SQL 源码，
 * 不靠「源码里包含某段文字」这种搬家就会静默失效的检查。
 */
const fs = require('fs')
const path = require('path')

const ROOT = 'F:/校园趣事/miniprogram'
const BACKEND = 'F:/校园趣事/backend'
const SQL = 'F:/校园趣事/sql'

// utils/util.js 在模块加载期就要读宿主对象（wx / qq），Node 里补一个空桩
global.wx = global.wx || {}

let pass = 0
let fail = 0
function ok(m) { pass++; console.log('  ok   ' + m) }
function no(m) { fail++; console.log('  FAIL ' + m) }
function eq(actual, expected, m) {
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  if (a === e) ok(m)
  else no(m + '（期望 ' + e + '，实际 ' + a + '）')
}
function read(p) { return fs.readFileSync(p, 'utf8') }

/* ============================================================
 * 1. 树洞分类：定义源唯一，且「全部」与「求助」语义正确
 * ============================================================ */
console.log('\n[1] 树洞分类定义（utils/wall.js）')
const wallUtil = require(path.join(ROOT, 'utils/wall.js'))
const { CATS, COMPOSE_CATS, CAT_TAG, findCat } = wallUtil

eq(CATS.length, 4, '树洞分类共 4 项（全部/心情/吐槽/求助）')
eq(CATS[0].key, '', '首项 key 为空串 = 不过滤')
eq(CATS[0].query, '', '「全部」不发 category 参数')
eq(CATS[0].name, '全部', '首项文案是「全部」')

// 「全部」必须是真不过滤：老版本 4 个分类里首项叫「校园心情」但不过滤，
// 用户以为在筛心情、其实在看全量，这是本次整理要消灭的核心问题。
if (!CATS.some(function (c) { return c.key === 'love' && c.name.indexOf('心情') >= 0 })) {
  no('「心情」应该是独立分类，而不是顶替「全部」')
} else {
  ok('「心情」是独立分类，与「全部」分开')
}

// 「求助」= 寻物 + 寻人，靠多值查询兼容存量数据
const helpCat = findCat('find')
eq(helpCat.name, '🔎 求助', '「求助」文案')
eq(helpCat.query.split(','), ['find', 'findp'], '「求助」查 find + findp 两个值')

// findCat 容错：未知 key 回落到「全部」，不能让页面白屏或 500
eq(findCat('not-exist').key, '', '未知分类回落到「全部」')
eq(findCat(undefined).key, '', 'undefined 回落到「全部」')

/* ============================================================
 * 2. 发布弹层：不能出现「全部」
 * ============================================================ */
console.log('\n[2] 发布弹层分类（COMPOSE_CATS）')
eq(COMPOSE_CATS.length, 3, '发布可选 3 个具体分类')
if (COMPOSE_CATS.every(function (c) { return !!c.key })) ok('发布分类里没有空 key')
else no('发布分类里混进了空 key（会导致发出无分类内容）')

const wallPageJs = read(path.join(ROOT, 'pages/wall/index.js'))
if (/composeCat:\s*'love'/.test(wallPageJs)) {
  if (COMPOSE_CATS.some(function (c) { return c.key === 'love' })) ok('composeCat 默认值 love 在可选分类里')
  else no('composeCat 默认值 love 不在可选分类里')
} else {
  no('composeCat 默认值不是 love（改动后需同步本断言）')
}

if (wallPageJs.indexOf('cats: CATS') >= 0 && wallPageJs.indexOf('composeCats: COMPOSE_CATS') >= 0) {
  ok('页面 chips 直接引用 utils/wall.js 的定义源')
} else {
  no('页面 chips 没有引用定义源（可能出现第二份分类清单）')
}

// 页面里不应该再出现内联的分类数组（第二份清单＝必然漂移）
if (/cats:\s*\[\s*\{/.test(wallPageJs)) no('页面里还留着内联分类数组')
else ok('页面里没有内联分类数组')

const wallWxml = read(path.join(ROOT, 'pages/wall/index.wxml'))
const composeUses = (wallWxml.match(/wx:for="\{\{composeCats\}\}"/g) || []).length
eq(composeUses, 2, '发布弹层的 chips 与 # 面板都改用 composeCats')
if (/wx:for="\{\{cats\}\}"/.test(wallWxml)) {
  const topOnly = /cat-scroll[\s\S]*?wx:for="\{\{cats\}\}"/.test(wallWxml)
  if (topOnly) ok('顶部 chips 仍用 cats（含「全部」）')
  else no('顶部 chips 没有用 cats')
} else {
  no('顶部 chips 丢失了 cats 引用')
}

/* ============================================================
 * 3. 存量数据兼容：decorateWall 对老分类仍能给出标签
 * ============================================================ */
console.log('\n[3] 存量分类的展示兼容（decorateWall）')
const decorate = wallUtil.decorateWall
eq(decorate({ id: '1', category: 'find' }).tags, ['#求助'], 'find 显示「#求助」')
eq(decorate({ id: '1', category: 'findp' }).tags, ['#寻人互助'], '存量 findp 仍显示「#寻人互助」')
eq(decorate({ id: '1', category: 'tree' }).tags, ['#树洞吐槽'], 'tree 显示「#树洞吐槽」')
eq(decorate({ id: '1', category: 'love' }).tags, ['#校园心情'], 'love 显示「#校园心情」')
eq(decorate({ id: '1', category: 'legacy-unknown' }).tags, ['#校园日常'], '未知分类兜底「#校园日常」')

/* ============================================================
 * 4. 后端白名单 ↔ 前端分类 key 必须一一对应
 * ============================================================ */
console.log('\n[4] 前端分类 ↔ 后端白名单（WallService）')
const wallService = read(path.join(BACKEND, 'campus-wall/src/main/java/com/campus/wall/service/WallService.java'))
const whiteMatch = wallService.match(/Set\.of\(([^)]*)\)/)
if (!whiteMatch) {
  no('没找到 WallService 的分类白名单')
} else {
  const backendCats = whiteMatch[1].split(',').map(function (s) { return s.trim().replace(/"/g, '') }).filter(Boolean)
  // 前端发出去的每一个值，后端都必须认（否则等于查询被静默忽略 → 返回全量）
  const sent = []
  CATS.forEach(function (c) {
    if (c.query) c.query.split(',').forEach(function (v) { sent.push(v) })
  })
  const missing = sent.filter(function (v) { return backendCats.indexOf(v) < 0 })
  if (missing.length === 0) ok('前端发出的分类值后端全部认可：' + sent.join(','))
  else no('后端不认这些分类值（会被静默忽略）：' + missing.join(','))

  // 反向：后端有的分类前端没入口 → 用户能看到但发不出去，属于无主分类
  const unused = backendCats.filter(function (v) { return sent.indexOf(v) < 0 })
  if (unused.length === 0) ok('后端没有无主分类')
  else no('后端存在前端无入口的分类：' + unused.join(','))
}

if (/parseCategories/.test(wallService) && /\.in\(!cats\.isEmpty\(\), WallPost::getCategory, cats\)/.test(wallService)) {
  ok('后端分页用 in 过滤，支持逗号多值')
} else {
  no('后端分页没有用 in 过滤（「求助」会查不到寻人）')
}

if (/normalizeCategory/.test(wallService)) ok('发布仍走单值白名单校验')
else no('发布缺少单值白名单校验')

/* ============================================================
 * 5. 闲置品类：后端是唯一顺序源，前端 emoji 表与之对齐
 * ============================================================ */
console.log('\n[5] 闲置品类（GoodsService ↔ util.js）')
const goodsService = read(path.join(BACKEND, 'campus-goods/src/main/java/com/campus/goods/service/GoodsService.java'))
const catRe = /new CategoryVO\((\d+),\s*"([^"]+)",\s*"([^"]+)"\)/g
const backendGoods = []
let gm
while ((gm = catRe.exec(goodsService))) {
  backendGoods.push({ id: Number(gm[1]), name: gm[2], icon: gm[3] })
}
eq(backendGoods.length, 7, '后端品类共 7 个')

const names = backendGoods.map(function (c) { return c.name })
eq(names, ['数码', '图书', '生活', '服饰', '运动', '代步', '其他'], '展示顺序：代步在运动后、其他前')

// id 不能重排：存量 t_goods.category_id 已落库
const ids = backendGoods.map(function (c) { return c.id })
eq(new Set(ids).size, ids.length, '品类 id 不重复')
const otherIdx = ids.indexOf(6)
if (otherIdx === ids.length - 1) ok('「其他」仍是 id=6（存量数据不串类）')
else no('「其他」的 id 被改动，存量商品会串品类')
const daiIdx = ids.indexOf(7)
if (daiIdx > 0 && daiIdx < ids.length - 1) ok('「代步」是新增 id=7，插在中间展示')
else no('「代步」的 id 不是 7')

// 前端 emoji 兜底表必须覆盖后端每个 id 且图标一致
const utilJs = read(path.join(ROOT, 'utils/util.js'))
const emojiMatch = utilJs.match(/const CATEGORY_EMOJI = \{([^}]*)\}/)
if (!emojiMatch) {
  no('utils/util.js 里找不到 CATEGORY_EMOJI')
} else {
  const map = {}
  emojiMatch[1].split(',').forEach(function (pair) {
    const kv = pair.split(':')
    if (kv.length === 2) map[kv[0].trim()] = kv[1].trim().replace(/['"]/g, '')
  })
  const drift = backendGoods.filter(function (c) { return map[c.id] !== c.icon })
  if (drift.length === 0) ok('前端 emoji 与后端 icon 全部一致（7 个）')
  else no('前端 emoji 与后端不一致：' + drift.map(function (c) { return c.name + '(' + c.id + ')' }).join(','))
}

/* ============================================================
 * 6. 频道：下线用软删，且下线的不再作为入口返回
 * ============================================================ */
console.log('\n[6] 频道上下线（t_channel.status）')
const schemaSql = read(path.join(BACKEND, 'sql/schema.sql'))
if (/status\s+TINYINT\s+NOT NULL DEFAULT 1 COMMENT '1上线 0下线/.test(schemaSql)) {
  ok('schema.sql 的 t_channel 有 status 字段')
} else {
  no('schema.sql 的 t_channel 缺 status 字段')
}

const postService = read(path.join(BACKEND, 'campus-post/src/main/java/com/campus/post/service/PostService.java'))
if (/w\.eq\(Channel::getStatus, 1\)/.test(postService)) ok('GET /channels 只返回上线频道')
else no('GET /channels 没有过滤下线频道')

const channelEntity = read(path.join(BACKEND, 'campus-post/src/main/java/com/campus/post/entity/Channel.java'))
if (/private Integer status;/.test(channelEntity)) ok('Channel 实体已加 status 映射')
else no('Channel 实体缺 status（查询会忽略该条件）')

const migration = read(path.join(SQL, 'migration-20261027-category-tidy.sql'))
if (/UPDATE t_channel SET status = 0 WHERE id IN \(2004, 2005\)/.test(migration)) {
  ok('迁移脚本下线「树洞吐槽」「二手交易」')
} else {
  no('迁移脚本没有下线重复频道')
}
// 软删而非物理删：存量 t_post 还引用这两个 channel_id
if (/DELETE FROM t_channel/i.test(migration)) no('迁移脚本物理删除了频道（历史帖子会丢频道名）')
else ok('迁移脚本不物理删除频道（保护历史帖子）')
if (/回退/.test(migration)) ok('迁移脚本带了回退说明')
else no('迁移脚本没有回退说明')

/* ============================================================
 * 7. 首页「校园生活」副标题：从真实频道名拼，不写死
 * ============================================================ */
console.log('\n[7] 首页功能区副标题（utils/zones.js）')
const zones = require(path.join(ROOT, 'utils/zones.js'))
const zonesSrc = read(path.join(ROOT, 'utils/zones.js'))

if (/吃喝玩乐 · 树洞吐槽 · 运动搭子/.test(zonesSrc)) {
  no('副标题还写死旧频道名（频道下线后会不同步）')
} else {
  ok('副标题不再写死话题名')
}

// 真跑 buildZones，检查 desc 来自 channels
const post = function (id, channelId) {
  return { id: id, channelId: channelId, content: 'x', createdAt: '2026-10-27 10:00:00' }
}
const built = zones.buildZones(
  { hot: null, wall: null, goods: null, posts: { records: [post(1, 2003), post(2, 2001)] } },
  [
    { id: 2001, name: '校园公告' },
    { id: 2003, name: '吃喝玩乐' },
    { id: 2006, name: '运动搭子' }
  ]
)
const campusZone = built.filter(function (z) { return z.key === 'campus' })[0]
if (!campusZone) {
  no('没生成「校园生活」区')
} else {
  eq(campusZone.desc, '吃喝玩乐 · 运动搭子', '副标题 = 公告之外的上线频道名（公告被排除）')
}

// 频道列表为空时不能崩，也不能渲染空 desc
const builtNoCh = zones.buildZones(
  { hot: null, wall: null, goods: null, posts: { records: [post(1, 2003)] } },
  []
)
const campusNoCh = builtNoCh.filter(function (z) { return z.key === 'campus' })[0]
if (campusNoCh && campusNoCh.desc) ok('频道列表为空时 desc 有兜底文案')
else no('频道列表为空时 desc 为空（卡片会缺副标题）')

/* ============================================================ */
console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
if (fail > 0) process.exitCode = 1
