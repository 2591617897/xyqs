/**
 * 校验 msg 分包四个通知页：四件套齐全、include/wxss 引用可达、
 * wxml 里的事件方法在页面 js 或 common/notice.js 中存在、data 字段齐备。
 */
const fs = require('fs')
const path = require('path')
const ROOT = 'F:/校园趣事/miniprogram'

const PAGES = ['notice/like', 'notice/fans', 'notice/comment', 'notice/system']
let bad = 0
function fail(m) { console.log('✗ ' + m); bad++ }
function ok(m) { console.log('✓ ' + m) }

const common = fs.readFileSync(path.join(ROOT, 'subpackages/msg/common/notice.js'), 'utf8')
const commonWxml = fs.readFileSync(path.join(ROOT, 'subpackages/msg/common/notice.wxml'), 'utf8')

// 1. 公共模板里事件方法是否都在 common/notice.js 里实现
const handlers = new Set()
const re = /(?:bind|catch):?[a-zA-Z]+="([^"{}]+)"/g
let m
while ((m = re.exec(commonWxml))) handlers.add(m[1])
handlers.forEach(function (h) {
  if (common.indexOf(h + ': function') < 0) fail('公共模板事件未实现：' + h)
})
ok('公共模板事件方法 ' + handlers.size + ' 个全部有实现：' + Array.from(handlers).join(', '))

// 2. 四个页面
PAGES.forEach(function (p) {
  const base = path.join(ROOT, 'subpackages/msg', p)
  ;['.js', '.json', '.wxml', '.wxss'].forEach(function (ext) {
    if (!fs.existsSync(base + ext)) fail(p + ext + ' 缺失')
  })
  const json = JSON.parse(fs.readFileSync(base + '.json', 'utf8'))
  if (!json.usingComponents) fail(p + '.json 缺 usingComponents（会白屏）')
  const wxml = fs.readFileSync(base + '.wxml', 'utf8')
  const inc = /<include\s+src="([^"]+)"/.exec(wxml)
  if (!inc) fail(p + '.wxml 没有 include')
  else {
    const target = path.resolve(path.dirname(base + '.wxml'), inc[1])
    if (!fs.existsSync(target)) fail(p + '.wxml 的 include 指向不存在的文件：' + inc[1])
  }
  const wxss = fs.readFileSync(base + '.wxss', 'utf8')
  const imp = /@import\s+"([^"]+)"/.exec(wxss)
  if (!imp) fail(p + '.wxss 没有 @import')
  else {
    const target = path.resolve(path.dirname(base + '.wxss'), imp[1])
    if (!fs.existsSync(target)) fail(p + '.wxss 的 @import 指向不存在的文件：' + imp[1])
  }
  const js = fs.readFileSync(base + '.js', 'utf8')
  if (js.indexOf('Page(createNoticePage(') < 0) fail(p + '.js 没有用 createNoticePage 注册页面')
  if (js.indexOf("require('../common/notice')") < 0) fail(p + '.js 的 require 路径不对')
})
ok('四个页面四件套 / include / @import / Page() 注册全部就位')

// 3. app.json 注册与预下载
const app = JSON.parse(fs.readFileSync(path.join(ROOT, 'app.json'), 'utf8'))
const sp = (app.subPackages || []).find(function (x) { return x.root === 'subpackages/msg' })
if (!sp) fail('app.json 没注册 subpackages/msg')
else if (sp.pages.length !== PAGES.length) fail('分包页面数不符')
else ok('app.json 已注册 msg 分包（' + sp.pages.join(', ') + '）')
const pre = app.preloadRule && app.preloadRule['pages/messages/index']
if (!pre || (pre.packages || []).indexOf('subpackages/msg') < 0) fail('缺 pages/messages/index 的 preloadRule')

// 4. 消息页已不再有抽屉
const msgJs = fs.readFileSync(path.join(ROOT, 'pages/messages/index.js'), 'utf8')
const msgWxml = fs.readFileSync(path.join(ROOT, 'pages/messages/index.wxml'), 'utf8')
const msgWxss = fs.readFileSync(path.join(ROOT, 'pages/messages/index.wxss'), 'utf8')
if (/notifyDrawer/.test(msgJs) || /notifyDrawer/.test(msgWxml)) fail('消息页仍有 notifyDrawer 残留')
if (/\.nd-item|\.notify-drawer/.test(msgWxss)) fail('消息页仍有抽屉样式残留')
if (msgJs.indexOf("'/subpackages/msg/notice/like'") < 0) fail('消息页 onQuick 没指向分包页面')
ok('消息页抽屉已彻底移除，四宫格改为 navigateTo 分包页面')

// 5. 占位检查：四宫格 data-name 与映射表是否一一对应
const names = []
const nre = /data-name="([^"]+)"/g
while ((m = nre.exec(msgWxml))) { if (m[1].indexOf('{{') < 0) names.push(m[1]) }
names.forEach(function (n) {
  if (msgJs.indexOf("'" + n + "': '/subpackages/msg/") < 0) fail('四宫格「' + n + '」在 NOTICE_PAGE 里没有对应页面')
})
ok('四宫格 ' + names.length + ' 个入口全部有独立落点：' + names.join(' / '))

console.log(bad ? '\n结果：失败 ' + bad + ' 项' : '\n结果：全部通过')
process.exit(bad ? 1 : 0)
