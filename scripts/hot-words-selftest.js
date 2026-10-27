#!/usr/bin/env node
/**
 * 搜索热词自测：静态一致性 + 规则镜像用例。
 *
 * 覆盖三段：
 *   1. 后端：建表脚本 / 实体 / Mapper / Service / Controller / 分页记录点
 *   2. 前端：兜底词、TTL 缓存、失败降级、mock 路由顺序
 *   3. 规则：前缀折叠、长度与字符校验的行为用例（JS 镜像 Java 实现）
 *
 * 跑法：node scripts/hot-words-selftest.js
 */

const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..')
const MP = path.join(ROOT, 'miniprogram')
const BE = path.join(ROOT, 'backend')

let pass = 0
let fail = 0
const failures = []

function ok(cond, name, detail) {
  if (cond) {
    pass++
  } else {
    fail++
    failures.push(name + (detail ? '（' + detail + '）' : ''))
    console.log('  ✗ ' + name + (detail ? '（' + detail + '）' : ''))
  }
}

function read(p) {
  return fs.readFileSync(p, 'utf8')
}

function has(src, needle, name) {
  ok(src.indexOf(needle) >= 0, name)
}

function section(title) {
  console.log('\n—— ' + title + ' ——')
}

/* ══════════════════════ 1. 建表脚本 ══════════════════════ */

section('1. 建表脚本')

const sqlPath = path.join(BE, 'sql/migration-20261027-goods-hot-words.sql')
ok(fs.existsSync(sqlPath), '迁移脚本存在')
const sql = fs.existsSync(sqlPath) ? read(sqlPath) : ''

has(sql, 'CREATE TABLE IF NOT EXISTS t_goods_search_log', '表名正确（IF NOT EXISTS 可重复执行）')
has(sql, 'UNIQUE KEY uk_day_user_word (search_day, user_id, norm_keyword)', '唯一键支撑「单人单日单词去重 → 刷榜失效」')
has(sql, 'user_id      BIGINT      NOT NULL DEFAULT 0', 'user_id 非空（NULL 在唯一索引里不去重，游客必须记 0）')
has(sql, 'result_count INT         NOT NULL DEFAULT 0', '记录了命中数（0 条的词不能上榜）')
has(sql, 'KEY idx_day_result (search_day, result_count)', '聚合索引覆盖 where + 排序')
has(sql, 'search_day   DATE', '按天分桶字段存在')

// 索引里如果有 idx_norm_day，说明考虑了「按词查历史」
has(sql, 'idx_norm_day', '按词回溯的索引存在（排查用）')

/* ══════════════════════ 2. 后端代码 ══════════════════════ */

section('2. 后端：实体 / Mapper')

const entityPath = path.join(BE, 'campus-goods/src/main/java/com/campus/goods/entity/GoodsSearchLog.java')
ok(fs.existsSync(entityPath), '实体 GoodsSearchLog 存在')
const entity = fs.existsSync(entityPath) ? read(entityPath) : ''

has(entity, '@TableName("t_goods_search_log")', '实体绑定表名')
// 实体字段必须和建表脚本一一对应，否则 MyBatis-Plus 插入会少列
const entityFields = ['keyword', 'normKeyword', 'userId', 'schoolId', 'city', 'resultCount', 'searchDay', 'createdAt']
entityFields.forEach(function (f) {
  ok(new RegExp('private \\w+ ' + f + ';').test(entity), '实体字段 ' + f + ' 存在')
})

const mapperPath = path.join(BE, 'campus-goods/src/main/java/com/campus/goods/mapper/GoodsSearchLogMapper.java')
ok(fs.existsSync(mapperPath), 'Mapper 存在')
const mapper = fs.existsSync(mapperPath) ? read(mapperPath) : ''

has(mapper, 'INSERT IGNORE INTO t_goods_search_log', '用 INSERT IGNORE（唯一键冲突是正常路径，不该抛异常）')
has(mapper, 'result_count > 0', '热词排除「搜了没结果」的词')
has(mapper, 'HAVING COUNT(*) >= #{minHits}', '有最低人次门槛，滤掉一次性偶发词')
has(mapper, 'CHAR_LENGTH(norm_keyword) >= 2', 'SQL 层也挡了一遍超短词')
has(mapper, 'ORDER BY hits DESC', '按人次倒序')
has(mapper, 'LIMIT #{limit}', 'limit 由参数控制（不是写死）')
has(mapper, 'DELETE FROM t_goods_search_log WHERE search_day < #{before}', '有过期清理语句')
has(mapper, 'import java.time.LocalDate;', 'LocalDate 已导入')

section('3. 后端：Service 规则')

const svcPath = path.join(BE, 'campus-goods/src/main/java/com/campus/goods/service/HotWordsService.java')
ok(fs.existsSync(svcPath), 'HotWordsService 存在')
const svc = fs.existsSync(svcPath) ? read(svcPath) : ''

has(svc, 'private static final int MIN_HITS = 2', '最低人次 = 2')
has(svc, 'CACHE_MS = 5 * 60_000L', '榜单缓存 5 分钟')
has(svc, 'SETTLE_MS = 3_000L', '连续输入 3 秒内只记最后一个词')
has(svc, 'UserContext.userId()', '记录者身份取自当前请求线程')
has(svc, 'settler.schedule', '落库在延迟线程里执行，不阻塞搜索')
has(svc, 'longWord.getWord().startsWith(shortWord.getWord())', '前缀折叠：识别输入中间态')
has(svc, 'hits(longWord) >= hits(shortWord)', '前缀折叠阈值：长词人次不低于短词才丢短词')
has(svc, 'securityGuard.purify(null, word, SecurityGuard.BIZ_GOODS)', '展示前过本地敏感词库')
has(svc, '@PreDestroy', '停机时关闭延迟线程池')
has(svc, 'MAX_LEN = 24', '词长上限 24')
has(svc, 'DIGITS_ONLY', '纯数字不作品牌词')

// 兜底词必须和前端 / mock 三处一致
const backendFallback = (svc.match(/FALLBACK = List\.of\(([\s\S]*?)\)/) || [])[1] || ''
const backendWords = (backendFallback.match(/"[^"]+"/g) || []).map(function (s) {
  return s.slice(1, -1)
})

section('4. 后端：接口与统计入口')

const ctrlPath = path.join(BE, 'campus-goods/src/main/java/com/campus/goods/controller/GoodsController.java')
const ctrl = read(ctrlPath)

has(ctrl, '@GetMapping("/hot-words")', '接口 GET /goods/hot-words 已注册')
has(ctrl, 'hotWordsService.top(days, limit)', '接口委托给 HotWordsService')
has(ctrl, 'private final HotWordsService hotWordsService;', 'Controller 已注入 HotWordsService')

const goodsSvc = read(path.join(BE, 'campus-goods/src/main/java/com/campus/goods/service/GoodsService.java'))
has(goodsSvc, 'hotWordsService.record(keyword, p.getTotal(), schoolId, city)', '分页接口顺手记搜索词')
has(goodsSvc, 'if (userId == null && page <= 1)', '只记首屏、且排除个人主页（浏览不是搜索）')

const app = read(path.join(BE, 'campus-goods/src/main/java/com/campus/goods/GoodsApplication.java'))
has(app, '@EnableScheduling', '开启了 @EnableScheduling（不加则清理任务静默不生效）')

const taskPath = path.join(BE, 'campus-goods/src/main/java/com/campus/goods/task/SearchLogCleanTask.java')
ok(fs.existsSync(taskPath), '清理任务存在')
if (fs.existsSync(taskPath)) {
  const task = read(taskPath)
  has(task, '@Scheduled', '清理任务有调度注解')
  has(task, 'cleanExpired()', '调用 Service 的清理方法')
}

/* ══════════════════════ 5. 前端 ══════════════════════ */

section('5. 前端：热词拉取与降级')

const listJs = read(path.join(MP, 'pages/goods/list.js'))

has(listJs, 'const FALLBACK_HOTS =', '前端有兜底热词常量')
has(listJs, 'hots: FALLBACK_HOTS', 'data.hots 初始用兜底词（首屏不等接口）')
has(listJs, "http.get('/goods/hot-words'", '调用了热词接口')
has(listJs, 'days: 7', '统计窗口 7 天')
has(listJs, 'HOTS_TTL', '有本地 TTL，避免重复请求')
has(listJs, 'this.loadHots()', 'onLoad 触发拉取')
has(listJs, 'self._hotsAt = 0', '失败后重置时间戳，下次还能重试')
has(listJs, '{ silent: true }', '失败静默（有兜底词，不该弹错误提示）')

// 前端兜底词
const frontList = (listJs.match(/FALLBACK_HOTS = \[([^\]]*)\]/) || [])[1] || ''
const frontWords = (frontList.match(/'[^']+'/g) || []).map(function (s) {
  return s.slice(1, -1)
})

const mockJs = read(path.join(MP, 'utils/mock.js'))
const mockList = (mockJs.match(/MOCK_HOT_WORDS = \[([^\]]*)\]/) || [])[1] || ''
const mockWords = (mockList.match(/'[^']+'/g) || []).map(function (s) {
  return s.slice(1, -1)
})

ok(backendWords.length > 0, '后端兜底词能解析出来', backendWords.join('/'))
ok(frontWords.length === backendWords.length, '前后端兜底词条数一致',
  '前端 ' + frontWords.length + ' / 后端 ' + backendWords.length)
ok(JSON.stringify(frontWords) === JSON.stringify(backendWords), '前后端兜底词内容与顺序一致',
  frontWords.join('/') + ' vs ' + backendWords.join('/'))
ok(JSON.stringify(mockWords) === JSON.stringify(backendWords), 'mock 热词与后端兜底一致',
  mockWords.join('/'))

// mock 路由顺序：hot-words 必须排在「两位 GET → 详情」之前
const hotIdx = mockJs.indexOf("s[1] === 'hot-words'")
const detailIdx = mockJs.indexOf("if (s.length === 2 && M === 'GET') return ok(goodsDetail(s[1]))")
ok(hotIdx > 0, 'mock 有 hot-words 路由')
ok(hotIdx > 0 && detailIdx > 0 && hotIdx < detailIdx, 'mock 的 hot-words 路由排在商品详情之前',
  'hot=' + hotIdx + ' detail=' + detailIdx)

/* ══════════════════════ 6. 规则镜像用例 ══════════════════════ */

section('6. 规则镜像：前缀折叠 / 归一化')

/** 与 HotWordsService#foldPrefixes 完全同构的实现 */
function foldPrefixes(rows) {
  const byLength = rows.slice().sort(function (a, b) {
    const diff = b.word.length - a.word.length
    return diff !== 0 ? diff : b.hits - a.hits
  })
  const kept = []
  byLength.forEach(function (shortWord) {
    let intermediate = false
    kept.forEach(function (longWord) {
      if (longWord.word.indexOf(shortWord.word) === 0 && longWord.hits >= shortWord.hits) {
        intermediate = true
      }
    })
    if (!intermediate) kept.push(shortWord)
  })
  return kept.sort(function (a, b) { return b.hits - a.hits }).map(function (r) { return r.word })
}

const foldCases = [
  {
    name: '输入中间态被折叠：考研书 5 次时，考/考研 被摘掉',
    rows: [{ word: '考', hits: 1 }, { word: '考研', hits: 5 }, { word: '考研书', hits: 5 }],
    expect: ['考研书']
  },
  {
    name: '真实高频短词保留：手机 100 次 vs 手机壳 30 次',
    rows: [{ word: '手机', hits: 100 }, { word: '手机壳', hits: 30 }],
    expect: ['手机', '手机壳']
  },
  {
    name: '短词人气更高时保留：苹果 40 次 vs 苹果手机 10 次',
    rows: [{ word: '苹果', hits: 40 }, { word: '苹果手机', hits: 10 }],
    expect: ['苹果', '苹果手机']
  },
  {
    name: '无人次为 0 的边界：长词 0 次不折叠短词',
    rows: [{ word: '台灯', hits: 3 }, { word: '台灯护眼', hits: 0 }],
    expect: ['台灯', '台灯护眼']
  },
  {
    name: '完全无关的词互不影响',
    rows: [{ word: '自行车', hits: 8 }, { word: '显示器', hits: 4 }],
    expect: ['自行车', '显示器']
  }
]

foldCases.forEach(function (c) {
  const got = foldPrefixes(c.rows)
  ok(JSON.stringify(got) === JSON.stringify(c.expect), c.name, '实得 ' + JSON.stringify(got))
})

/** 与 HotWordsService#normalize 的判定部分同构（拆词用空格代替 SearchKit） */
const ALLOWED = /^[\u4e00-\u9fa5a-zA-Z0-9 \u3000·\-_.+&/、]+$/
const DIGITS_ONLY = /^[0-9 \u3000]+$/

function normalizeLike(keyword) {
  if (!keyword || !String(keyword).trim()) return null
  const norm = String(keyword).trim().split(/[\s\u3000]+/).slice(0, 6).join(' ')
  const len = Array.from(norm).length
  if (len < 2 || len > 24) return null
  if (!ALLOWED.test(norm) || DIGITS_ONLY.test(norm)) return null
  return norm
}

const normCases = [
  ['自行车', '自行车', '正常词通过'],
  ['苹果　　18', '苹果 18', '全角空格折叠成单空格（与拆词口径一致）'],
  ['  考研书  ', '考研书', '首位空白被 trim'],
  ['a', null, '单字符不成词'],
  ['18', null, '纯数字不作品牌词'],
  ['自行车🚲', null, 'emoji 不在白名单（榜上不出现奇怪符号）'],
  ['<script>', null, '尖括号被挡'],
  ['', null, '空串忽略'],
  ['苹果 手机 壳 保护套 硅胶 透明 防摔', '苹果 手机 壳 保护套 硅胶 透明', '超过 6 个词截断，与 SearchKit 一致']
]

normCases.forEach(function (c) {
  const got = normalizeLike(c[0])
  ok(got === c[1], '归一化：' + c[2], '入参「' + c[0] + '」实得 ' + JSON.stringify(got))
})

/* ══════════════════════ 汇总 ══════════════════════ */

console.log('\n' + '='.repeat(46))
if (fail === 0) {
  console.log('全部通过：' + pass + ' 项断言')
} else {
  console.log('通过 ' + pass + ' 项，失败 ' + fail + ' 项：')
  failures.forEach(function (f) { console.log('  - ' + f) })
}
console.log('='.repeat(46))

process.exit(fail === 0 ? 0 : 1)
