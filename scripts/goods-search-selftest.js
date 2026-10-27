/**
 * 闲置列表页「搜索」自测：
 *  1) wxml 里所有事件绑定在 js 里都有实现（缺一个就是点了没反应）
 *  2) 搜索相关 data 字段齐备
 *  3) 关键词高亮 splitHighlight / 多词语义（真跑 utils/kw.js，不靠肉眼）
 *  4) 发起请求时 keyword 用的是 activeKeyword，且 trim 过
 */
const fs = require('fs')
const path = require('path')
const ROOT = 'F:/校园趣事/miniprogram'
const js = fs.readFileSync(path.join(ROOT, 'pages/goods/list.js'), 'utf8')
const wxml = fs.readFileSync(path.join(ROOT, 'pages/goods/list.wxml'), 'utf8')

let pass = 0, fail = 0
function ok(m) { pass++; console.log('  ok   ' + m) }
function no(m) { fail++; console.log('  FAIL ' + m) }

/* ---------- 1. 事件绑定 ↔ 方法实现 ---------- */
const handlers = new Set()
const hre = /(?:bind|catch):?[a-zA-Z]+="([^"{}]+)"/g
let m
while ((m = hre.exec(wxml))) handlers.add(m[1])
handlers.forEach(function (h) {
  if (js.indexOf(h + ': function') >= 0) ok('事件已实现：' + h)
  else no('事件没有实现：' + h)
})

/* ---------- 2. data 字段 ---------- */
;['keyword', 'activeKeyword', 'kwWords', 'history', 'hots', 'showPanel'].forEach(function (k) {
  if (new RegExp('\\n\\s+' + k + ':').test(js)) ok('data 字段存在：' + k)
  else no('data 字段缺失：' + k)
})

/* ---------- 3. 高亮 / 拆词：直接 require 真实模块 ----------
   函数已搬到 utils/kw.js（后端 SearchKit 的前端镜像）。
   不再从页面源码里抠字符串——搬一次家就会静默失效，那种测试不如没有。 */
const kwUtil = require(path.join(ROOT, 'utils/kw.js'))
const splitHighlight = kwUtil.splitHighlight

function segs(text, kw) {
  return splitHighlight(text, kw).map(function (s) { return (s.hit ? '[' + s.t + ']' : s.t) }).join('')
}

const cases = [
  ['苹果18', '苹果', '[苹果]18', '前缀命中'],
  ['出二手苹果手机', '苹果', '出二手[苹果]手机', '中间命中'],
  ['苹果手机 苹果耳机', '苹果', '[苹果]手机 [苹果]耳机', '多处命中'],
  ['iPhone 15', 'iphone', '[iPhone] 15', '大小写不敏感但保留原文'],
  ['山地自行车', '显示器', '山地自行车', '无命中保持整段'],
  ['自行车', '', '自行车', '空关键词不高亮'],
  ['', '苹果', '', '空标题不炸'],
  ['aaa', 'aa', '[aa]a', '重叠只切一次'],
  ['¥100', '¥', '[¥]100', '特殊字符不炸'],
  // —— 多词 ——
  ['苹果手机 九成新', '苹果 手机', '[苹果][手机] 九成新', '多词同时高亮'],
  ['苹果手机', '苹果 手机', '[苹果][手机]', '相邻词各切一段'],
  ['苹果手机壳', '苹果 手机', '[苹果][手机]壳', '前缀重叠不吞词'],
  ['iPhone 手机 九成新', 'iphone 手机', '[iPhone] [手机] 九成新', '大小写 + 多词'],
  ['苹果耳机', '苹果 手机', '[苹果]耳机', '只命中一部分词也标出来'],
  ['全角　空格分隔', '全角 空格', '[全角]　[空格]分隔', '全角空格当分隔符'],
  ['aaabbb', 'aa bb', '[aa]a[bb]b', '两个词各标各的，不重复消费字符']
]
cases.forEach(function (c) {
  const got = segs(c[0], c[1])
  if (got === c[2]) ok('高亮 · ' + c[3] + ' → ' + got)
  else no('高亮 · ' + c[3] + ' 期望 ' + c[2] + '，实际 ' + got)
})

/* ---------- 3b. 多词语义：词之间 AND ---------- */
;[
  ['苹果手机 九成新', '苹果 手机', true, '两个词都命中'],
  ['苹果耳机', '苹果 手机', false, '缺一个词就不算命中'],
  ['苹果手机', '  苹果   手机  ', true, '多余空白不影响'],
  ['苹果手机', '苹果　手机', true, '全角空格当分隔符'],
  ['iPhone 15', 'iphone 15', true, '大小写不敏感'],
  ['任何文本', '', true, '空关键词不过滤'],
  ['任意', '%%%', true, '只由通配符组成的词等于没输入']
].forEach(function (c) {
  const got = kwUtil.matchText(c[0], c[1])
  if (got === c[2]) ok('匹配 · ' + c[3])
  else no('匹配 · ' + c[3] + ' 期望 ' + c[2] + '，实际 ' + got)
})

/* 多字段：每个词都要有字段命中（不要求同一个字段） */
const rows = [
  { name: '张三', text: '出 iPhone 15' },
  { name: '李四', text: '出 iPhone 15' }
]
const hit = kwUtil.filterList(rows, ['name', 'text'], '张三 iphone')
if (hit.length === 1 && hit[0].name === '张三') ok('多字段 · 昵称命中 A 词 + 正文命中 B 词 → 命中')
else no('多字段匹配不正确，实际 ' + JSON.stringify(hit))

const hit2 = kwUtil.filterList(rows, ['name', 'text'], '张三 显示器')
if (hit2.length === 0) ok('多字段 · 有一个词全字段都没命中 → 过滤掉')
else no('多字段应过滤掉，实际 ' + hit2.length)

/* 拆词上限 / 去重 */
const w6 = kwUtil.words('a b c d e f g h')
if (w6.length === 6) ok('拆词 · 最多 6 个词（防止有人粘一整段话）')
else no('拆词上限不对，实际 ' + w6.length)

const wdup = kwUtil.words('iPhone iphone IPHONE')
if (wdup.length === 1) ok('拆词 · 忽略大小写去重')
else no('拆词没有去重，实际 ' + wdup.length)

/* ---------- 4. 请求参数来源 ---------- */
if (/if \(this\.data\.activeKeyword\) params\.keyword = this\.data\.activeKeyword/.test(js)) {
  ok('请求参数取 activeKeyword（不是输入框实时值）')
} else no('请求参数没有取 activeKeyword')

if (/String\(this\.data\.keyword \|\| ''\)\.trim\(\)/.test(js)) ok('关键词做了 trim')
else no('关键词没有 trim')

if (/_reqId/.test(js)) ok('有请求序号防覆盖（旧响应不会盖新结果）')
else no('缺少请求序号，快速切词可能被旧结果覆盖')

if (/onUnload[\s\S]{0,120}clearTimeout\(this\._searchTimer\)/.test(js)) ok('onUnload 清理了防抖定时器')
else no('onUnload 没有清理定时器（会有离页后继续请求的内存泄漏）')

if (/catchtap="onDelHistory"/.test(wxml)) ok('删除历史用 catchtap（不会冒泡成搜索）')
else no('删除历史没有用 catchtap')

/* ---------- 5. 多词提示是否透出给用户 ---------- */
if (wxml.indexOf('空格分隔多个关键词') >= 0) ok('搜索面板说明了多词规则')
else no('搜索面板没有告诉用户可以用空格分隔')

if (/kwWords\.length > 1/.test(wxml)) ok('多词时提示「N 个词」并给出删词引导')
else no('多词状态没有提示')

console.log('\n========== 通过 ' + pass + ' / 失败 ' + fail + ' ==========')
process.exit(fail ? 1 : 0)
