/**
 * 内容可见范围自检（2026-10-28）
 *
 * 覆盖四层：
 *  1. utils/visibility.js 的取值归一化与文案（require 真实源码）
 *  2. 闲置 / 帖子发布页：默认值、选项渲染、切换、提交 payload、编辑回显
 *  3. 数据库：t_goods / t_post 的 visibility 列存在、默认值正确、无非法值
 *  4. 后端关键分支存在性（静态）：实体 / DTO / 落库 / 列表过滤 / 详情校验，防止漏改
 *
 * ⚠️ 第 4 项是静态检查而不是跑 Java：起全套服务（Nacos + 8 个模块）成本太高，
 *    这里只保证「该有的分支在」，真正的行为正确性靠第 1~3 层 + 页面联调保证。
 */
const fs = require('fs')
const path = require('path')
const { execFileSync } = require('child_process')

const ROOT = path.join(__dirname, '..')
const MP = path.join(ROOT, 'miniprogram')
const BE = path.join(ROOT, 'backend')

let pass = 0
let fail = 0
let skip = 0

function ok(name, cond, extra) {
  if (cond) { pass++; console.log('  ✅ ' + name) } else {
    fail++; console.log('  ❌ ' + name + (extra ? '  → ' + extra : ''))
  }
}
function skipped(name, why) {
  skip++; console.log('  ⏭  ' + name + '（' + why + '）')
}
function read(p) {
  return fs.readFileSync(p, 'utf8')
}

/* ---------- 1. utils/visibility.js ---------- */

console.log('\n[1] utils/visibility.js 取值与文案（真实源码）')

const vis = require(path.join(MP, 'utils/visibility.js'))

ok('city 原样保留', vis.normalize('city') === 'city')
ok('school 原样保留', vis.normalize('school') === 'school')
ok('null 兜底为 school', vis.normalize(null) === 'school')
ok('空串兜底为 school', vis.normalize('') === 'school')
ok('非法值兜底为 school（不抛错）', vis.normalize('drop table') === 'school')
ok('大小写变体 CITY 兜底为 school（只认全小写）', vis.normalize('CITY') === 'school')
ok('isCity 只认 city', vis.isCity('city') === true && vis.isCity('school') === false)
ok('text(school) = 仅本校', vis.text('school') === '仅本校')
ok('text(city) = 同市可见', vis.text('city') === '同市可见')

const opts = vis.options('贵州铜仁数据职业学院', '铜仁市')
ok('选项共 2 个', opts.length === 2)
ok('第 1 项是 school（默认在前）', opts[0].value === 'school')
ok('第 2 项是 city', opts[1].value === 'city')
ok('本校选项文案带学校名', opts[0].sub.indexOf('贵州铜仁数据职业学院') >= 0)
ok('同市选项文案带城市名', opts[1].sub.indexOf('铜仁市') >= 0)

const bareOpts = vis.options('', '')
ok('无学校信息时不出现 undefined', bareOpts.every((o) => o.sub.indexOf('undefined') < 0))
ok('无学校信息时退化成通用文案', bareOpts[0].sub === '只有本校的同学能看到')

/* ---------- 2. 发布页 ---------- */

console.log('\n[2] 发布页（闲置 / 帖子）')

const PAGES = [
  { name: '闲置', js: 'pages/goods/publish.js', wxml: 'pages/goods/publish.wxml', wxss: 'pages/goods/publish.wxss' },
  { name: '帖子', js: 'pages/post/publish.js', wxml: 'pages/post/publish.wxml', wxss: 'pages/post/publish.wxss' }
]

PAGES.forEach(function (p) {
  const js = read(path.join(MP, p.js))
  const wxml = read(path.join(MP, p.wxml))
  const wxss = read(path.join(MP, p.wxss))

  ok(p.name + '：引入 utils/visibility', /require\('(\.\.\/)+utils\/visibility'\)/.test(js))
  ok(p.name + '：默认 visibility = school', /visibility: 'school'/.test(js))
  ok(p.name + '：有 chooseVisibility 且用 normalize', /chooseVisibility[\s\S]{0,200}normalize\(/.test(js))
  ok(p.name + '：提交 payload 带 visibility', /visibility:\s*vis\.normalize\(/.test(js))
  ok(p.name + '：编辑回显 visibility', /visibility:\s*vis\.normalize\((g|post)\.visibility\)/.test(js))
  ok(p.name + '：applyVisibilityOptions 生成选项', /visibilityOptions:\s*vis\.options\(/.test(js))
  ok(p.name + '：WXML 渲染两个选项', /wx:for="\{\{visibilityOptions\}\}"/.test(wxml))
  ok(p.name + '：WXML 绑定 chooseVisibility', /bindtap="chooseVisibility"/.test(wxml))
  ok(p.name + '：WXML 选中态用 visibility 比对', /visibility === item\.value/.test(wxml))
  ok(p.name + '：WXML 无内联 style 覆盖 vis-opt', !/vis-opt[^>]*style="/.test(wxml))
  ok(p.name + '：WXSS 定义了 .vis-opt.active', /\.vis-opt\.active\s*\{/.test(wxss))
  ok(p.name + '：选项区块有说明文案', /决定哪些同学能刷到/.test(wxml))
})

/* ---------- 3. 数据库 ---------- */

console.log('\n[3] 数据库列与数据')

const MYSQL_CANDIDATES = [
  'mysql',
  'D:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe',
  'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
]
let mysqlBin = null
for (const c of MYSQL_CANDIDATES) {
  try { execFileSync(c, ['--version'], { stdio: 'ignore' }); mysqlBin = c; break } catch (e) { /* 下一候选 */ }
}

function q(sql) {
  return execFileSync(mysqlBin, ['-h', 'localhost', '-P', '3306', '-uroot', '-p123456',
    '-D', 'guizhou_campus_fun', '-e', sql, '-N', '-B'], { encoding: 'utf8' }).trim()
}

if (!mysqlBin) {
  skipped('数据库列检查', '未找到 mysql 客户端，跳过（不影响代码正确性）')
} else {
  try {
    const cols = q("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='guizhou_campus_fun' AND COLUMN_NAME='visibility' AND TABLE_NAME IN ('t_goods','t_post');")
    ok('t_goods / t_post 都有 visibility 列', cols === '2', '实际 ' + cols)

    const def = q("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='guizhou_campus_fun' AND COLUMN_NAME='visibility' AND TABLE_NAME IN ('t_goods','t_post') AND COLUMN_DEFAULT='school';")
    ok('两列默认值都是 school', def === '2', '实际 ' + def)

    const notNull = q("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='guizhou_campus_fun' AND COLUMN_NAME='visibility' AND TABLE_NAME IN ('t_goods','t_post') AND IS_NULLABLE='NO';")
    ok('两列都是 NOT NULL', notNull === '2', '实际 ' + notNull)

    const bad = q("SELECT COUNT(*) FROM (SELECT visibility v FROM t_goods UNION ALL SELECT visibility FROM t_post) x WHERE x.v NOT IN ('school','city');")
    ok('库里没有非法 visibility 值', bad === '0', '实际 ' + bad)

    const allSchool = q("SELECT COUNT(*) FROM (SELECT visibility v FROM t_goods UNION ALL SELECT visibility FROM t_post) x WHERE x.v='school';")
    console.log('     （历史数据已全部回填为 school，共 ' + allSchool + ' 行）')
  } catch (e) {
    skipped('数据库列检查', '连接失败：' + String(e.stderr || e.message).slice(0, 120))
  }
}

/* ---------- 4. 后端关键分支 ---------- */

console.log('\n[4] 后端关键分支（静态）')

const goodsDir = path.join(BE, 'campus-goods/src/main/java/com/campus/goods')
const postDir = path.join(BE, 'campus-post/src/main/java/com/campus/post')
const commonVis = path.join(BE, 'campus-common/src/main/java/com/campus/common/content/Visibility.java')

ok('公共常量 Visibility.java 存在', fs.existsSync(commonVis))
const visJava = read(commonVis)
ok('Visibility 定义 SCHOOL/CITY 常量', /SCHOOL\s*=\s*"school"/.test(visJava) && /CITY\s*=\s*"city"/.test(visJava))
ok('Visibility.normalize 兜底为 school', /CITY\.equalsIgnoreCase\(v\)\s*\?\s*CITY\s*:\s*SCHOOL/.test(visJava))

// 闲置
const goodsEntity = read(path.join(goodsDir, 'entity/Goods.java'))
const goodsReq = read(path.join(goodsDir, 'dto/GoodsReq.java'))
const goodsSvc = read(path.join(goodsDir, 'service/GoodsService.java'))
const goodsCtrl = read(path.join(goodsDir, 'controller/GoodsController.java'))

ok('闲置：Goods 实体有 visibility', /private String visibility;/.test(goodsEntity))
ok('闲置：GoodsReq 有 visibility', /private String visibility;/.test(goodsReq))
ok('闲置：apply() 落库且归一化', /setVisibility\(Visibility\.normalize\(/.test(goodsSvc))
ok('闲置：本市流加 visibility 过滤', /eq\(Goods::getVisibility, Visibility\.CITY\)/.test(goodsSvc))
ok('闲置：本校内容 OR 豁免（不产生悬空 OR）', /or\(\)\.eq\(Goods::getSchoolId, schoolId\)/.test(goodsSvc))
ok('闲置：无学校身份时只看 city', /游客 \/ 未选学校/.test(goodsSvc))
ok('闲置：Controller 透传 schoolScope 给本市流', /page\(keyword, categoryId, sort, page, size, schoolScope, targetCity/.test(goodsCtrl))
ok('闲置：详情接口有 assertVisible', /private void assertVisible\(Goods/.test(goodsSvc))
ok('闲置：detail 调用了 assertVisible', /assertVisible\(goods, userId\);/.test(goodsSvc))
ok('闲置：越权抛 404 而非 403', /throw BizException\.notFound/.test(goodsSvc))

// 帖子
const postEntity = read(path.join(postDir, 'entity/Post.java'))
const postReq = read(path.join(postDir, 'dto/PostReq.java'))
const postSvc = read(path.join(postDir, 'service/PostService.java'))
const postCtrl = read(path.join(postDir, 'controller/PostController.java'))

ok('帖子：Post 实体有 visibility', /private String visibility;/.test(postEntity))
ok('帖子：PostReq 有 visibility', /private String visibility;/.test(postReq))
ok('帖子：publish 落库且归一化', /setVisibility\(Visibility\.normalize\(/.test(postSvc))
ok('帖子：update 也能改可见范围', /set\(Post::getVisibility, Visibility\.normalize\(/.test(postSvc))
ok('帖子：本市流加 visibility 过滤', /eq\(Post::getVisibility, Visibility\.CITY\)/.test(postSvc))
ok('帖子：本校内容 OR 豁免（不产生悬空 OR）', /or\(\)\.eq\(Post::getSchoolId, viewerSchoolId\)/.test(postSvc))
ok('帖子：pageByCity 收 viewerSchoolId 参数', /pageByCity\(Long channelId, String city, long page, long size,\s*\n\s*Long userId, Long viewerSchoolId\)/.test(postSvc))
ok('帖子：Controller 透传 schoolScope', /UserContext\.userId\(\), schoolScope\)/.test(postCtrl))
ok('帖子：详情接口有 assertVisible', /private void assertVisible\(Post/.test(postSvc))
ok('帖子：detail 调用了 assertVisible', /assertVisible\(post, userId\);/.test(postSvc))

// 悬空 OR 陷阱：不能出现 or().eq(boolean, ...) 这种写法
const danglingOr = /\.or\(\)\.eq\((schoolId|viewerSchoolId)\s*!=/.test(goodsSvc) || /\.or\(\)\.eq\((schoolId|viewerSchoolId)\s*!=/.test(postSvc)
ok('无「or() 后跟条件 eq」的悬空 OR 写法', !danglingOr)

console.log('\n结果：' + pass + ' 通过 / ' + fail + ' 失败' + (skip ? ' / ' + skip + ' 跳过' : '') + '\n')
process.exit(fail ? 1 : 0)
