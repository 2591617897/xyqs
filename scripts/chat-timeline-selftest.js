/**
 * 群聊时间轴回归测试（scripts/chat-timeline-selftest.js）
 * ------------------------------------------------------------------
 * 覆盖 subpackages/msg/group/chat.js 里的 relayoutAll（真正的代码，不复制一遍）：
 *   1. 用 require.cache 注入假的 request / ws / util，让 chat.js 能被 require；
 *   2. 捕获 Page() 注册的配置，直接调 relayoutAll；
 *   3. 断言时间分隔条的分布与文案、引用块时间的回填。
 *
 * 为什么值得单独测：时间分隔条是「跟相邻消息比较」算出来的，
 * 一旦 lastTs 的推进写错（例如本地乐观消息的 createdAt 为空把 lastTs 清成 0），
 * 表现为「一条消息都不显示时间」或「每条都显示时间」，肉眼很容易漏。
 */
const path = require('path')

/* ---------- 宿主环境桩：chat.js 只用到 mp.* 与 toast ---------- */
const mp = new Proxy({}, {
  get: () => function () {
    return {
      then: (fn) => Promise.resolve().then(fn),
      catch: (fn) => Promise.resolve().then(fn)
    }
  }
})

const chatPath = path.resolve(__dirname, '../miniprogram/subpackages/msg/group/chat.js')

/* 先把三个依赖塞进 require.cache，chat.js 里 require 时才不会真的去加载
   （真实文件在模块顶层就摸 wx.*，node 下会直接抛） */
function stub(relPath, exports) {
  const full = require.resolve(path.resolve(path.dirname(chatPath), relPath))
  require.cache[full] = { id: full, filename: full, loaded: true, exports: exports }
}
stub('../../../utils/request', { get: () => Promise.resolve(), post: () => Promise.resolve() })
stub('../utils/ws', {
  on() {}, off() {}, connect() {}, join() {}, leave() {},
  shutdown() {}, isOpen: () => true, sendMsg: () => true
})
stub('../../../utils/util', { mp: mp, toast() {} })

let page = null
global.Page = function (cfg) { page = cfg }

require(chatPath)

if (!page || typeof page.relayoutAll !== 'function') {
  console.error('✗ chat.js 没有注册 Page 或缺少 relayoutAll')
  process.exit(1)
}

/* ---------- 断言工具 ---------- */
let pass = 0
const fails = []
function ok(cond, label, extra) {
  if (cond) { pass++ } else { fails.push(label + (extra ? ' → ' + extra : '')) }
}

/* ---------- 造数据 ---------- */
function ts(offsetMin) {
  // 相对「现在」偏移多少分钟，输出后端同款时间串 yyyy-MM-dd HH:mm:ss
  const d = new Date(Date.now() - offsetMin * 60000)
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) +
    ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
}
function msg(id, offsetMin, extra) {
  return Object.assign({
    id: String(id),
    senderId: '2',
    senderName: '校友' + id,
    content: '内容' + id,
    type: 'text',
    createdAt: offsetMin === null ? '' : ts(offsetMin),
    mine: false,
    deleted: 0
  }, extra || {})
}

/* ---------- 用例 1：5 分钟以内的连续消息只出现一次时间 ---------- */
{
  const list = [msg(1, 60), msg(2, 58), msg(3, 57), msg(4, 40)]
  const out = page.relayoutAll(list)
  const shown = out.filter((m) => m.showTime).map((m) => m.id)
  ok(shown.length === 2, '用例1: 应只有 2 条显示时间分隔条', '实际 ' + JSON.stringify(shown))
  ok(shown[0] === '1' && shown[1] === '4', '用例1: 首条与跨 5 分钟那条各一条分隔条', JSON.stringify(shown))
  ok(out[0].timeLabel.length > 0, '用例1: 分隔条文案不能为空')
  ok(out[1].timeLabel === '' && out[2].timeLabel === '', '用例1: 未到 5 分钟不给文案')
}

/* ---------- 用例 2：正好 5 分钟 = 显示（>= 而非 >） ---------- */
{
  const out = page.relayoutAll([msg(1, 100), msg(2, 95)])
  ok(out[1].showTime === true, '用例2: 间隔正好 5 分钟应显示分隔条')
}

/* ---------- 用例 3：本地乐观消息（createdAt 为空）不能清空 lastTs ---------- */
{
  // 真实 1 → 本地 2（无时间）→ 真实 3，间隔 30 分钟
  const out = page.relayoutAll([msg(1, 120), msg('local-x', null, { mine: true }), msg(3, 90)])
  ok(out[0].showTime === true, '用例3: 首条显示分隔条')
  ok(out[1].showTime === false, '用例3: 本地消息不显示分隔条')
  ok(out[2].showTime === true, '用例3: 本地消息之后的跨时消息仍要显示分隔条')
}

/* ---------- 用例 4：隔天文案带「昨天」，跨年带年份 ---------- */
{
  const now = new Date()
  const yst = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1, 9, 5, 0)
  const p = (n) => (n < 10 ? '0' + n : '' + n)
  const ystStr = yst.getFullYear() + '-' + p(yst.getMonth() + 1) + '-' + p(yst.getDate()) + ' 09:05:00'
  const out = page.relayoutAll([{ id: '1', createdAt: ystStr, review: 0 }])
  ok(/^昨天 09:05$/.test(out[0].timeLabel), '用例4: 昨天应显示「昨天 HH:mm」', out[0].timeLabel)
}

/* ---------- 用例 5：引用块时间按 replyToId 回查被引用消息 ---------- */
{
  const list = [
    msg(1, 50),
    msg(2, 49, { replyToId: '1', replyToName: '校友1', replyToText: '内容1', replyToTime: '' })
  ]
  const out = page.relayoutAll(list)
  ok(out[1].replyToTime && out[1].replyToTime.length > 0, '用例5: 引用块时间应被回填', out[1].replyToTime)
  ok(/^\d{2}:\d{2}$/.test(out[1].replyToTime), '用例5: 引用时间是 HH:mm 格式', out[1].replyToTime)
}

/* ---------- 用例 6：被引用消息已不在列表 → 不报错、不回填 ---------- */
{
  const out = page.relayoutAll([
    msg(9, 30, { replyToId: '404', replyToName: '某人', replyToText: '早被清掉了' })
  ])
  ok(out[0].replyToTime === '' || out[0].replyToTime === undefined, '用例6: 找不到原消息时引用时间留空')
  ok(out[0].replyToName === '某人', '用例6: 引用文案不能丢')
}

/* ---------- 用例 7：空列表 / undefined 不炸 ---------- */
{
  ok(Array.isArray(page.relayoutAll([])), '用例7: 空数组返回数组')
  ok(Array.isArray(page.relayoutAll(null)), '用例7: null 返回数组')
}

/* ---------- 用例 8：图片消息的形状（不能走 @ 解析，默认无尺寸） ---------- */
{
  const ctx = { data: { myId: '1', myAvatar: '' } }
  // URL 里故意带一个 @：图片消息绝不能被当成含 @ 的文本来切片段
  const out = page.decorate.call(ctx, [{
    id: '9',
    senderId: '2',
    senderName: '校友',
    type: 'image',
    content: 'https://campus-api.trkcwl.com/uploads/2026/10/@2x_abc.jpg',
    createdAt: ts(10),
    deleted: 0
  }], true)
  ok(out[0].type === 'image', '用例8: 图片消息 type 保持 image')
  ok(out[0].segments === null, '用例8: 图片消息不做 @ 片段解析', JSON.stringify(out[0].segments))
  ok(out[0].imgW === 0 && out[0].imgH === 0, '用例8: 服务端消息默认无尺寸（等 getImageInfo 补）')
  ok(out[0].uploading === false, '用例8: 服务端消息不是上传中状态')
}

/* ---------- 汇总 ---------- */
if (fails.length) {
  console.error('✗ 群聊时间轴自测失败 ' + fails.length + ' 项：')
  fails.forEach((f) => console.error('   - ' + f))
  process.exit(1)
}
console.log('✓ 群聊消息自测通过（' + pass + ' 项断言）：时间分隔条聚合 / 本地消息不污染 / 昨天文案 / 引用时间回填 / 图片消息形状')
