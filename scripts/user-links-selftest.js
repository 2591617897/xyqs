/**
 * 「我的」页跳转链接自检（2026-10-27）
 *
 * 覆盖两件事：
 *  1. pages/user/index.js 的 goSocial / openOrders 产出的 URL 与分支是否正确
 *  2. subpackages/me/dynamic/index.js 收到 ?tab=&cat= 后的落位是否精确
 *
 * 第 2 项直接 require 真实页面 js（stub 掉 utils 依赖），验证的是源码本身，
 * 不是另写一份等价实现。
 */
const fs = require('fs')
const path = require('path')
const Module = require('module')

const ROOT = path.join(__dirname, '..', 'miniprogram')
let pass = 0
let fail = 0

function ok(name, cond, extra) {
  if (cond) { pass++; console.log('  ✅ ' + name) } else {
    fail++; console.log('  ❌ ' + name + (extra ? '  → ' + extra : ''))
  }
}

/* ---------- 1. user/index.js 的跳转产出 ---------- */

const userSrc = fs.readFileSync(path.join(ROOT, 'pages/user/index.js'), 'utf8')
const wxmlSrc = fs.readFileSync(path.join(ROOT, 'pages/user/index.wxml'), 'utf8')

console.log('\n[1] 我的页跳转函数与绑定')

// goMessages 必须只剩「消息通知与隐私权限」一个入口
const msgBinds = (wxmlSrc.match(/bindtap="goMessages"/g) || []).length
ok('goMessages 只剩 1 个入口（消息通知与隐私权限）', msgBinds === 1, '实际 ' + msgBinds)

// 社交互动区三个入口全部改绑 goSocial，且不能再指 goMessages
ok('「校园回声」已改绑 goSocial', /校园回声[^>]*bindtap="goSocial"|bindtap="goSocial"[^>]*>校园回声/.test(wxmlSrc))
ok('「发起的表白」带 tab=wall&cat=love', /data-tab="wall"[\s\S]{0,200}?data-cat="love"/.test(wxmlSrc))
ok('「树洞秘语」带 tab=wall&cat=tree', /data-tab="wall"[\s\S]{0,200}?data-cat="tree"/.test(wxmlSrc))
ok('社交互动区不再出现 goMessages', !/g2-item[^>]*goMessages/.test(wxmlSrc))

// 意向单走 seller 分支
ok('「意向单」带 data-role="seller"', /data-role="seller"/.test(wxmlSrc))
ok('openOrders 读 data-role 决定分支', /orderRole:\s*d\.role === 'seller' \? 'seller' : 'buyer'/.test(userSrc))
ok('openOrders 每次显式写 orderRole（不留上次分支）', /showOrders: true,[\s\S]{0,80}orderRole/.test(userSrc))

// goPublish 死代码已清
ok('goPublish 死代码已删除', !/goPublish/.test(userSrc))

// goSocial 只在有 data 时拼 query，无参时是干净路径
ok('goSocial 无参时不拼多余 ?', /qs\.length \? '\?' \+ qs\.join\('&'\) : ''/.test(userSrc))

/* ---------- 2. dynamic 页真实逻辑 ---------- */

console.log('\n[2] 我的动态页 ?tab=&cat= 落位（真实源码）')

const nav = []
const stubHttp = { get: () => Promise.reject(new Error('unused')), USE_MOCK: false }
const stubAuth = { ensureLogin: () => Promise.resolve({}) }
const stubMp = {
  setNavigationBarTitle: (o) => nav.push(o.title),
  navigateTo: () => {}
}
const stubUtil = { mp: stubMp, toast: () => {}, timeAgo: () => '' }
const stubImg = { resolveList: (l) => l }

const origLoad = Module._load
Module._load = function (request) {
  if (request.indexOf('utils/request') >= 0) return stubHttp
  if (request.indexOf('utils/auth') >= 0) return stubAuth
  if (request.indexOf('utils/util') >= 0) return stubUtil
  if (request.indexOf('utils/img') >= 0) return stubImg
  return origLoad.apply(this, arguments)
}

let pageObj = null
global.Page = function (o) { pageObj = o }
global.getApp = function () { return { globalData: {} } }

require(path.join(ROOT, 'subpackages/me/dynamic/index.js'))
Module._load = origLoad

/** 造一个最小 page 实例：data + setData + 页面对象上的全部方法（switchTab 内部会调 applyTab） */
function mkPage(data) {
  const inst = {
    data: Object.assign({ tab: 'all', cat: '', master: [], shown: [], tabs: [], emptyTitle: '' }, data),
    setData: function (patch) { Object.assign(this.data, patch) }
  }
  Object.keys(pageObj).forEach(function (k) {
    if (typeof pageObj[k] === 'function') inst[k] = pageObj[k]
  })
  return inst
}

const MASTER = [
  { _type: 'post', _key: 'p1', category: undefined, title: '帖子A' },
  { _type: 'wall', _key: 'w1', category: 'love', title: '表白1' },
  { _type: 'wall', _key: 'w2', category: 'tree', title: '吐槽1' },
  { _type: 'wall', _key: 'w3', category: 'love', title: '表白2' },
  { _type: 'wall', _key: 'w4', category: 'find', title: '寻物1' }
]

// 无参进入 → 全部
let p = mkPage({ master: MASTER })
pageObj.onLoad.call(p, {})
ok('无参 → tab=all、cat 为空', p.data.tab === 'all' && p.data.cat === '')
pageObj.applyTab.call(p, p.data.tab)
ok('无参展示全部 5 条', p.data.shown.length === 5, '实际 ' + p.data.shown.length)

// 「发起的表白」：?tab=wall&cat=love
nav.length = 0
p = mkPage({ master: MASTER })
pageObj.onLoad.call(p, { tab: 'wall', cat: 'love' })
ok('cat=love → tab 强制 wall', p.data.tab === 'wall')
ok('cat=love → 导航标题换成「我的表白」', nav[nav.length - 1] === '💗 我的表白', '实际 ' + nav[nav.length - 1])
pageObj.applyTab.call(p, p.data.tab)
ok('cat=love 只筛出 2 条表白', p.data.shown.length === 2, '实际 ' + p.data.shown.length)
ok('cat=love 不含帖子与吐槽', p.data.shown.every((i) => i._type === 'wall' && i.category === 'love'))
ok('cat=love 空态文案是「还没有发过表白」', p.data.emptyTitle === '还没有发过表白')

// 「树洞秘语」：?tab=wall&cat=tree
nav.length = 0
p = mkPage({ master: MASTER })
pageObj.onLoad.call(p, { tab: 'wall', cat: 'tree' })
ok('cat=tree → 导航标题「我的树洞」', nav[nav.length - 1] === '🌳 我的树洞', '实际 ' + nav[nav.length - 1])
pageObj.applyTab.call(p, p.data.tab)
ok('cat=tree 只筛出 1 条吐槽', p.data.shown.length === 1, '实际 ' + p.data.shown.length)

// 白名单外的 cat 必须被忽略（防 URL 注入任意值）
p = mkPage({ master: MASTER })
pageObj.onLoad.call(p, { tab: 'wall', cat: 'drop table' })
ok('非法 cat 被忽略（白名单校验）', p.data.cat === '', '实际 ' + JSON.stringify(p.data.cat))

// 手动切 tab 必须清掉 cat，否则用户点「树洞」看到的还是只有表白
nav.length = 0
p = mkPage({ master: MASTER })
pageObj.onLoad.call(p, { tab: 'wall', cat: 'love' })
pageObj.switchTab.call(p, { currentTarget: { dataset: { key: 'wall' } } })
ok('切 tab 后 cat 被清空', p.data.cat === '')
ok('切 tab 后标题还原「我的动态」', nav[nav.length - 1] === '我的动态', '实际 ' + nav[nav.length - 1])
ok('切 tab 后看到全部 4 条树洞', p.data.shown.length === 4, '实际 ' + p.data.shown.length)

// tab 计数始终按全量算（tab 是导航，不该被 cat 影响）
ok('tab 计数按全量（全部=5）', p.data.tabs[0].count === 5, '实际 ' + p.data.tabs[0].count)
ok('tab 计数按全量（树洞=4）', p.data.tabs[2].count === 4, '实际 ' + p.data.tabs[2].count)

console.log('\n结果：' + pass + ' 通过 / ' + fail + ' 失败\n')
process.exit(fail ? 1 : 0)
