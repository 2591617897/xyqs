/**
 * 多词搜索全链路自测。
 *
 * 为什么需要这个脚本：
 * 多词搜索的正确性不在某一个函数里，而是「拆词规则前后端一致 + 每个查询入口都接上了」。
 * 只要有一处漏改（比如某个后台 mapper 还在用 #{keyword}），表现为「某个页面搜多词就是空」，
 * 而这种缺漏肉眼极难发现——所以用静态断言把它们钉死。
 *
 * 覆盖：
 *  1) SearchKit 的拆词规则与常量
 *  2) 后台 7 个 MyBatis 查询都改成 keywords + foreach，没有残留 #{keyword}
 *  3) 后台 8 个 Service 都调了 SearchKit
 *  4) 有意保留单串的标识符搜索（工单号 / 操作日志账号）确实没被误改
 *  5) 前端 utils/kw.js 与后端常量一致（全角空格用行为断言，不玩字符串转义）
 *  6) 前端四处本地搜索（闲置 / 消息 / 学校 / mock）都接了 kw 工具
 */
const fs = require('fs')
const path = require('path')

const ROOT = 'F:/校园趣事'
const MP = path.join(ROOT, 'miniprogram')
const BE = path.join(ROOT, 'backend')
const ADMIN = path.join(BE, 'campus-admin/src/main/java/com/campus/admin')
const IDEOGRAPHIC_SPACE = String.fromCharCode(0x3000) // 全角空格

let pass = 0, fail = 0
function ok(m) { pass++; console.log('  ok   ' + m) }
function no(m) { fail++; console.log('  FAIL ' + m) }
function read(p) { return fs.readFileSync(p, 'utf8') }
function has(src, needle, msg) { if (src.indexOf(needle) >= 0) ok(msg); else no(msg + '（找不到：' + needle + '）') }
function hasNot(src, needle, msg) { if (src.indexOf(needle) < 0) ok(msg); else no(msg + '（仍存在：' + needle + '）') }

console.log('\n—— 1. 后端 SearchKit ——')
const kitPath = path.join(BE, 'campus-common/src/main/java/com/campus/common/search/SearchKit.java')
if (!fs.existsSync(kitPath)) {
  no('SearchKit 不存在：' + kitPath)
} else {
  const kit = read(kitPath)
  ok('SearchKit 存在')
  has(kit, 'int MAX_WORDS = 6', '最多 6 个词')
  has(kit, 'int MAX_WORD_LEN = 24', '单词最多 24 字')
  has(kit, 'u3000', '全角空格算分隔符')
  has(kit, 'public static List<String> words(String keyword)', '提供 words() 拆词')
  has(kit, 'public static <T> void andAllWords(', '提供 andAllWords() 叠加多词条件')
  // 关键：每个词必须独立包一层 and(...)，否则会和相邻 status 条件串成 A OR B AND C
  has(kit, 'w.and(q -> {', '每个词独立 and(...) 包括号（防优先级事故）')
}

console.log('\n—— 2. 后台 MyBatis 查询 ——')
const mappers = [
  ['GoodsQueryMapper.java', ['g.title LIKE', 'g.description LIKE'], '商品'],
  ['PostQueryMapper.java', ['p.title LIKE', 'p.content LIKE'], '帖子'],
  ['WallQueryMapper.java', ['w.content LIKE', 'w.anon_name LIKE'], '树洞'],
  ['CommentQueryMapper.java', ['t.content LIKE'], '评论'],
  ['ChatQueryMapper.java', ['ua.nickname LIKE', 'ub.nickname LIKE', 'm.content LIKE'], '私信'],
  ['UserQueryMapper.java', ['u.nickname LIKE', 'u.phone_hash = #{phoneHash}'], '用户'],
  ['RegionDictMapper.java', ['name LIKE'], '学校下拉']
]
mappers.forEach(function (item) {
  const file = item[0], cols = item[1], label = item[2]
  const src = read(path.join(ADMIN, 'mapper', file))
  // Java 注解里的 XML 可能用 'keywords' 也可能用 "keywords"（双引号需转义），
  // 只看「foreach 里出现 keywords」这个事实，不纠结引号形式
  const hasForeach = src.indexOf('<foreach') >= 0 && src.indexOf('keywords') >= 0
  if (hasForeach) ok(label + ' · 用 keywords 逐词循环')
  else no(label + ' · 没有 foreach collection=keywords')

  if (src.indexOf('@Param("keywords") List<String> keywords') >= 0) ok(label + ' · 参数类型改为 List<String>')
  else no(label + ' · 参数没有改成 List<String>')

  hasNot(src, '#{keyword}', label + ' · 没有残留 #{keyword}')

  cols.forEach(function (c) {
    has(src, c, label + ' · 仍匹配 ' + c)
  })

  // if 守卫也必须是 keywords.size()，否则空列表时 foreach 不生成条件但 if 仍进
  const guardOk = src.indexOf('keywords.size() &gt; 0') >= 0 || src.indexOf('keywords.size() > 0') >= 0
  if (guardOk) ok(label + ' · if 用 keywords.size() 判空')
  else no(label + ' · if 没有改成 keywords.size()')
})

console.log('\n—— 3. 后台 Service ——')
// 传 List 给 mapper 的
;[
  ['ContentService.java', 4, '内容（帖子/树洞/商品/评论）'],
  ['ChatService.java', 2, '私信'],
  ['RegionService.java', 1, '学校下拉'],
  ['UserManageService.java', 1, '用户']
].forEach(function (item) {
  const src = read(path.join(ADMIN, 'service', item[0]))
  const n = (src.match(/SearchKit\.words\(keyword\)/g) || []).length
  if (n >= item[1]) ok(item[2] + ' · SearchKit.words 调用 ' + n + ' 处')
  else no(item[2] + ' · SearchKit.words 只调了 ' + n + ' 处，期望 ≥' + item[1])
})

// 走 wrapper 的
;[
  ['AdminUserService.java', ['AdminUser::getUsername', 'AdminUser::getRealName'], '后台账号'],
  ['RoleService.java', ['AdminRole::getName', 'AdminRole::getCode'], '角色'],
  ['SensitiveService.java', ['SensitiveWord::getWord'], '敏感词'],
  ['NoticeService.java', ['AdminNotice::getTitle'], '公告'],
  ['AdService.java', ['AdSlot::getName', 'AdMaterial::getTitle'], '广告位/素材'],
  ['UserManageService.java', ['UserBlacklist::getUserName'], '黑名单']
].forEach(function (item) {
  const src = read(path.join(ADMIN, 'service', item[0]))
  const tag = item[1].join(' / ')
  if (src.indexOf('SearchKit.andAllWords(') >= 0) ok(tag + ' · 用 andAllWords 叠条件')
  else no(tag + ' · 没有调用 andAllWords')
  item[1].forEach(function (col) {
    has(src, col, tag + ' · 仍参与匹配 ' + col)
  })
  // 老的链式 like(...keyword) 必须清掉，否则会和 andAllWords 叠出双重条件
  if (/\.like\([^)]*keyword[^)]*\)/.test(src)) no(tag + ' · 仍有链式 .like(..., keyword) 残留')
  else ok(tag + ' · 没有残留链式 like')
})

console.log('\n—— 4. 有意保留单串的标识符搜索 ——')
// 工单号 / 操作日志账号本身就是单个 token，拆词只会让结果变空，所以刻意不改。
;[
  ['ReportService.java', 'ReportTicket::getTicketNo', '工单号'],
  ['LoginLogService.java', 'AdminLoginLog::getUsername', '登录日志账号'],
  ['OperationLogService.java', 'AdminOperationLog::getUsername', '操作日志账号']
].forEach(function (item) {
  const src = read(path.join(ADMIN, 'service', item[0]))
  has(src, item[1], item[2] + ' · 仍是单串搜索（有意保留）')
  hasNot(src, 'SearchKit', item[2] + ' · 没有误接入多词（标识符不该拆）')
})

console.log('\n—— 5. 前端 utils/kw.js 与后端规则一致 ——')
const kwPath = path.join(MP, 'utils/kw.js')
if (!fs.existsSync(kwPath)) {
  no('utils/kw.js 不存在')
} else {
  const kwSrc = read(kwPath)
  ok('utils/kw.js 存在')
  has(kwSrc, 'var MAX_WORDS = 6', '前端最多 6 个词（与 SearchKit 一致）')
  has(kwSrc, 'var MAX_WORD_LEN = 24', '前端单词上限 24（与 SearchKit 一致）')
  has(kwSrc, 'function matchText(', '提供 matchText（本地过滤）')
  has(kwSrc, 'function splitHighlight(', '提供 splitHighlight（高亮）')

  try {
    const kw = require(kwPath)

    // 全角空格：用行为断言，不去比字符串里的反斜杠（那种断言改一次转义就废）
    const full = kw.words('甲' + IDEOGRAPHIC_SPACE + '乙')
    if (full.length === 2) ok('全角空格确实当分隔符（行为验证）')
    else no('全角空格没被当分隔符，拆出 ' + full.length + ' 个词')

    const ws = kw.words('苹果 手机')
    if (ws.length === 2 && ws[0] === '苹果' && ws[1] === '手机') ok('拆词真跑：「苹果 手机」→ 2 个词')
    else no('拆词真跑结果异常：' + JSON.stringify(ws))

    if (kw.matchText('苹果手机 九成新', '苹果 手机') === true) ok('匹配真跑：两个词都命中 → true')
    else no('匹配真跑：两个词都命中却返回 false')

    if (kw.matchText('苹果耳机', '苹果 手机') === false) ok('匹配真跑：缺一个词 → false')
    else no('匹配真跑：缺一个词却返回 true')

    if (kw.words('a b c d e f g h').length === 6) ok('拆词真跑：最多 6 个词')
    else no('拆词上限不对')

    if (kw.words('iPhone iphone IPHONE').length === 1) ok('拆词真跑：忽略大小写去重')
    else no('拆词没有去重')
  } catch (e) {
    no('utils/kw.js 运行失败：' + e.message)
  }
}

console.log('\n—— 6. 前端本地搜索接入点 ——')
const frontPoints = [
  ['pages/goods/list.js', 'utils/kw', 'kwUtil.splitHighlight', '闲置列表（高亮）'],
  ['pages/messages/index.js', 'utils/kw', 'kwUtil.filterList', '消息会话'],
  ['utils/school.js', './kw', 'kwUtil.filterList', '学校选择'],
  ['utils/mock.js', './kw', 'kwUtil.matchText', 'mock 商品列表'],
  ['utils/mock.js', './kw', 'kwUtil.filterList', 'mock 学校列表']
]
frontPoints.forEach(function (item) {
  const src = read(path.join(MP, item[0]))
  has(src, item[1], item[3] + ' · 引入了 kw 工具')
  has(src, item[2], item[3] + ' · 用了 ' + item[2])
})

// 旧的整串匹配写法必须消失，否则等于两套语义并存
const msg = read(path.join(MP, 'pages/messages/index.js'))
hasNot(msg, '.indexOf(kw) >= 0', '消息页没有残留整串 indexOf 过滤')

const schoolSrc = read(path.join(MP, 'utils/school.js'))
hasNot(schoolSrc, 's.name.indexOf(kw) >= 0', '学校模块没有残留整串 indexOf 过滤')

console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
process.exit(fail ? 1 : 0)
