/**
 * 消息提醒功能自测
 *
 * 覆盖三层：
 *  1. 前端页面与路由（设置页四件套、app.json 注册、事件方法齐全、无深层嵌套三目）
 *  2. utils/subscribe.js 的真实行为（无模板不调接口、允许/拒绝/失败三条分支、模板数上限）
 *  3. 后端契约（接口存在、模板 id 来自配置、角标按开关过滤、unreadTotal 含通知）
 *
 * 运行：node scripts/message-notify-selftest.js
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..')
const MP = path.join(ROOT, 'miniprogram')
const BE = path.join(ROOT, 'backend')

let pass = 0
const fails = []

function ok(cond, msg) {
  if (cond) { pass++; return true }
  fails.push(msg)
  return false
}
function read(p) {
  try { return fs.readFileSync(p, 'utf8') } catch (e) { return null }
}
function has(src, sub, msg) {
  return ok(src != null && src.indexOf(sub) >= 0, msg)
}
function no(src, sub, msg) {
  return ok(src != null && src.indexOf(sub) < 0, msg)
}

/* ============================================================
   1. 设置页四件套
   ============================================================ */
const PAGE = 'subpackages/msg/settings/index'
;['js', 'wxml', 'wxss', 'json'].forEach(function (ext) {
  ok(fs.existsSync(path.join(MP, PAGE + '.' + ext)), '缺少设置页文件：' + PAGE + '.' + ext)
})

const pageJs = read(path.join(MP, PAGE + '.js'))
const pageWxml = read(path.join(MP, PAGE + '.wxml'))
const pageJson = read(path.join(MP, PAGE + '.json'))

ok(pageJs && /Page\s*\(\s*\{/.test(pageJs), '设置页 js 未调用 Page()（会白屏）')
has(pageJson, '"usingComponents"', '设置页 json 缺少 usingComponents（缺了整页白屏）')
has(pageJson, '"navigationBarTitleText"', '设置页 json 缺少标题')

/* 事件方法齐全：wxml 里绑定的每个 handler 都要在 js 里存在 */
const handlers = new Set()
let m
const hre = /(?:bind|catch):?[a-zA-Z]+="([^"{}]+)"/g
while ((m = hre.exec(pageWxml || ''))) handlers.add(m[1])
handlers.forEach(function (h) {
  ok(pageJs && pageJs.indexOf(h + ': function') >= 0, '设置页 wxml 绑定了 ' + h + ' 但 js 里没有实现')
})
// 5 个：开关 / 免打扰开关 / 免打扰起 / 免打扰止 / 微信授权
ok(handlers.size >= 5, '设置页事件绑定数量异常：' + handlers.size)

/* 五类开关 + 免打扰 + 订阅入口都要有 */
;['notifyMessage', 'notifyLike', 'notifyFollow', 'notifyComment', 'notifySystem'].forEach(function (k) {
  has(pageJs, k, '设置页缺少开关字段：' + k)
})
has(pageWxml, 'dndEnabled', '设置页缺少免打扰开关')
has(pageWxml, 'mode="time"', '设置页免打扰没用时间选择器')
has(pageWxml, 'onSubscribe', '设置页缺少微信提醒入口')

/* 刚定的规矩：WXML 禁止三层以上嵌套三目 */
ok((pageWxml || '').indexOf('))))') < 0, '设置页 wxml 存在多层嵌套三目，多一个括号就白屏')

/* ============================================================
   2. app.json 注册 + 预下载
   ============================================================ */
const app = JSON.parse(read(path.join(MP, 'app.json')))
const msgSp = (app.subPackages || []).concat(app.subpackages || []).filter(function (sp) {
  return sp.root === 'subpackages/msg'
})[0]
ok(!!msgSp, 'app.json 缺少 subpackages/msg 分包')
ok(msgSp && msgSp.pages.indexOf('settings/index') >= 0, 'app.json 未注册 settings/index')
ok(!!(app.preloadRule && app.preloadRule['pages/messages/index']),
  '消息页未配置 preloadRule，进设置页会等下载')

/* ============================================================
   3. 消息中心页接入
   ============================================================ */
const msgJs = read(path.join(MP, 'pages/messages/index.js'))
has(msgJs, "navigateTo({ url: '/subpackages/msg/settings/index' })",
  '「消息设置」按钮未跳转到设置页')
no(msgJs, '消息设置即将上线', '「消息设置」仍是 toast 占位')
has(msgJs, "'/messages/notifications/read-all'",
  '「全部已读」没有清站内通知，四宫格红点会残留')
has(msgJs, 'msg_tip_closed', '防骗贴士关闭状态未持久化，下次进页面还会出现')
has(msgJs, 'mp.setStorageSync(TIP_CLOSED_KEY', '防骗贴士未写入本地标记')
has(msgJs, 'mp.getStorageSync(TIP_CLOSED_KEY)', '防骗贴士未读取本地标记')

/* ============================================================
   4. utils/subscribe.js 真实行为
   ============================================================ */
const calls = []
global.wx = {
  requestSubscribeMessage: function (opt) {
    calls.push(opt.tmplIds.slice())
    const res = {}
    opt.tmplIds.forEach(function (id) { res[id] = global.__WX_VERDICT || 'accept' })
    if (global.__WX_FAIL) {
      opt.fail({ errMsg: 'requestSubscribeMessage:fail mock' })
      return
    }
    opt.success(Object.assign({ errMsg: 'requestSubscribeMessage:ok' }, res))
  },
  getStorageSync: function () { return null },
  setStorageSync: function () {},
  showToast: function () {}
}

const subscribe = require(path.join(MP, 'subpackages/msg/utils/subscribe.js'))

async function testSubscribe() {
  // 4.1 无模板：绝不能调接口（调了必失败，还会弹一个空模板的报错框）
  calls.length = 0
  let r = await subscribe.request([])
  ok(r.status === 0 && r.reason === 'no-template', '空模板应返回 no-template，实际：' + JSON.stringify(r))
  ok(calls.length === 0, '空模板时不应调用 wx.requestSubscribeMessage')

  r = await subscribe.request(null)
  ok(r.reason === 'no-template', 'tmplIds 为 null 时应安全返回')

  // 4.2 用户允许
  calls.length = 0
  global.__WX_VERDICT = 'accept'
  global.__WX_FAIL = false
  r = await subscribe.request(['tpl_a', 'tpl_b'])
  ok(r.status === 1 && r.accepted.length === 2, '全部允许时应 status=1，实际：' + JSON.stringify(r))
  ok(calls.length === 1 && calls[0].length === 2, '模板 id 未正确传给微信接口')

  // 4.3 用户拒绝
  global.__WX_VERDICT = 'reject'
  r = await subscribe.request(['tpl_a'])
  ok(r.status === 2 && r.reason === 'user-reject', '拒绝时应 status=2，实际：' + JSON.stringify(r))

  // 4.4 接口失败
  global.__WX_FAIL = true
  r = await subscribe.request(['tpl_a'])
  ok(r.status === 0 && r.reason === 'fail', '接口失败时应 status=0/fail，实际：' + JSON.stringify(r))
  global.__WX_FAIL = false

  // 4.5 模板数上限：超过 3 个只传前 3 个（微信侧超限会直接报错）
  calls.length = 0
  global.__WX_VERDICT = 'accept'
  r = await subscribe.request(['a', 'b', 'c', 'd', 'e'])
  ok(calls.length === 1 && calls[0].length === 3, '应只请求前 3 个模板，实际传了 ' + (calls[0] || []).length)

  // 4.6 脏数据过滤：空串 / 非字符串不能塞给微信
  calls.length = 0
  r = await subscribe.request(['', null, 123, 'real'])
  ok(calls.length === 1 && calls[0].length === 1 && calls[0][0] === 'real', '无效模板 id 未被过滤')

  // 4.7 必须在 tap 同步栈里发起：requestSubscribeMessage 要同步被调用
  calls.length = 0
  let syncCalled = false
  const origin = global.wx.requestSubscribeMessage
  global.wx.requestSubscribeMessage = function (opt) {
    syncCalled = true
    origin(opt)
  }
  subscribe.request(['tpl_a'])
  ok(syncCalled, 'request 未能同步调用微信接口（点击手势会失效）')
  global.wx.requestSubscribeMessage = origin
}

/* ============================================================
   5. 后端契约
   ============================================================ */
const ctrl = read(path.join(BE, 'campus-message/src/main/java/com/campus/message/controller/MessageSettingController.java'))
has(ctrl, '@RequestMapping("/messages/settings")', '后端缺少 /messages/settings 控制器')
has(ctrl, '@GetMapping', '后端缺少读取设置接口')
has(ctrl, '@PutMapping', '后端缺少保存设置接口')
has(ctrl, '@PostMapping("/subscribe")', '后端缺少订阅授权上报接口')
has(ctrl, 'UserContext.requireUserId()', '设置接口未使用登录态，可能读到别人的设置')
no(ctrl, 'getUserId()', '设置接口用了旧的身份取法')

const settingSvc = read(path.join(BE, 'campus-message/src/main/java/com/campus/message/service/MessageSettingService.java'))
has(settingSvc, 'subscribe-templates', '订阅模板未从配置读取（硬编码模板 id 换模板就要发版）')
has(settingSvc, 'effectiveFlags', '缺少有效开关，开关改不动角标')
has(settingSvc, 'HH_MM', '免打扰时间未做格式校验')
has(settingSvc, 'inDnd', '缺少免打扰区间判定（跨天要能算）')

const notiSvc = read(path.join(BE, 'campus-message/src/main/java/com/campus/message/service/NotificationService.java'))
has(notiSvc, 'effectiveFlags', '通知未读统计未按开关过滤')
has(notiSvc, 's.all = s.like + s.follow', '通知未读合计未由分项相加（关了类目总数还存在）')

const msgSvc = read(path.join(BE, 'campus-message/src/main/java/com/campus/message/service/MessageService.java'))
has(msgSvc, 'unreadNotice', '消息中心 summary 未返回通知未读分项')
has(msgSvc, 'unreadMessage + unreadNotice', '底栏角标口径未包含通知未读')

const mig = read(path.join(BE, 'sql/migration-20261027-message-setting.sql'))
has(mig, 't_message_setting', '缺少建表脚本')
has(mig, 'subscribe_status', '建表脚本缺少订阅授权状态列')
const schema = read(path.join(BE, 'sql/schema.sql'))
has(schema, 't_message_setting', 'schema.sql 未同步建表（新库部署会缺表）')

const yml = read(path.join(BE, 'campus-message/src/main/resources/application.yml'))
has(yml, 'WX_SUBSCRIBE_TEMPLATES', 'application.yml 缺少订阅模板环境变量')

/* 接口必须登录：网关的公开读前缀不能把 messages 放进去 */
const gw = read(path.join(BE, 'campus-gateway/src/main/java/com/campus/gateway/filter/AuthGlobalFilter.java'))
no(gw, '"/api/messages"', '网关把 /api/messages 加进了公开读前缀，设置接口会泄露')

/* ============================================================
   6. mock 覆盖
   ============================================================ */
const mock = read(path.join(MP, 'utils/mock.js'))
has(mock, "s[1] === 'settings'", 'mock 未覆盖 /messages/settings')
has(mock, "s[1] === 'notifications'", 'mock 未覆盖 /messages/notifications')
has(mock, 'templateIds: []', 'mock 不应伪造订阅模板 id（假 id 只会让授权必然失败）')
/* 路由顺序：settings/notifications 必须排在「两位路径 GET → 会话详情」之前 */
const idxSettings = mock.indexOf("s[1] === 'settings'")
const idxDetail = mock.indexOf("if (s.length === 2 && M === 'GET') return ok(convoDetail(s[1]))")
ok(idxSettings >= 0 && idxDetail >= 0 && idxSettings < idxDetail,
  'mock 路由顺序错误：/messages/settings 会被当成会话 id 查详情')

/* ============================================================
   跑异步部分再出报告
   ============================================================ */
testSubscribe().then(function () {
  console.log('')
  console.log('========== 消息提醒自测 ==========')
  if (fails.length) {
    fails.forEach(function (f) { console.log('  ✗ ' + f) })
    console.log('通过 ' + pass + ' / 失败 ' + fails.length)
    process.exit(1)
  }
  console.log('通过 ' + pass + ' / 失败 0')
  console.log('=================================')
}).catch(function (e) {
  console.error('自测异常：', e)
  process.exit(1)
})
