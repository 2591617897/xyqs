#!/usr/bin/env node
/**
 * 微信学生身份认证插件开关。
 *
 * 背景：app.json 只要声明了 plugins.studentVerify，而小程序后台没有添加过
 * 「微信学生身份验证」插件，开发者工具会**直接编译失败**（连首页都进不去）。
 * 所以插件声明不能长期写死在仓库里，必须做成可开关的。
 *
 * 用法：
 *   node scripts/wx-student-plugin.js on      开启（声明插件 + 页面注册组件 + 前端开关 true）
 *   node scripts/wx-student-plugin.js off     回退（三处一起还原）
 *   node scripts/wx-student-plugin.js status  只看当前状态
 *
 * 开启前必须先在微信侧做完（缺一不可）：
 *   1) 小程序后台「功能 → 学生验证」申请通过；
 *   2) 「设置 → 第三方设置 → 插件管理」添加「微信学生身份验证」；
 *   3) 后端 WX_STUDENT_VERIFY=true。
 */

const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..', 'miniprogram')
const APP_JSON = path.join(ROOT, 'app.json')
const PAGE_JSON = path.join(ROOT, 'pages', 'user', 'verify.json')
const CONFIG_JS = path.join(ROOT, 'utils', 'config.js')

/** 官方插件 appid，固定值 */
const PLUGIN_KEY = 'studentVerify'
const PLUGIN_PROVIDER = 'wxa16657d57059e0f0'
const PLUGIN_COMPONENT = 'plugin://studentVerify/verify'
/** 插件未开通时的同名占位组件：保证 WXML 里的 <studentVerify> 永远能解析到 */
const STUB_COMPONENT = '/components/student-verify-stub/index'

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

function writeJson(file, obj) {
  fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n', 'utf8')
}

function flipConfig(enabled) {
  const src = fs.readFileSync(CONFIG_JS, 'utf8')
  const next = src.replace(/(WX_STUDENT_VERIFY:\s*)(true|false)/, `$1${enabled}`)
  if (next === src) {
    throw new Error('utils/config.js 里没找到 WX_STUDENT_VERIFY 开关，请检查文件是否被改过')
  }
  fs.writeFileSync(CONFIG_JS, next, 'utf8')
}

function status() {
  const app = readJson(APP_JSON)
  const page = readJson(PAGE_JSON)
  const cfg = fs.readFileSync(CONFIG_JS, 'utf8')
  const flag = /WX_STUDENT_VERIFY:\s*true/.test(cfg)
  const declared = !!(app.plugins && app.plugins[PLUGIN_KEY])
  const comp = (page.usingComponents && page.usingComponents[PLUGIN_KEY]) || ''
  const usingPlugin = comp === PLUGIN_COMPONENT
  const usingStub = comp === STUB_COMPONENT
  console.log('前端开关 WX_STUDENT_VERIFY :', flag ? 'true' : 'false')
  console.log('app.json plugins 声明     :', declared ? '已声明' : '未声明')
  console.log('verify.json 组件          :', usingPlugin ? '插件' : (usingStub ? '本地占位组件' : (comp || '未注册')))
  const on = flag && declared && usingPlugin
  const off = !flag && !declared && usingStub
  console.log('结论                      :', on ? '三处一致（已开启）' : (off ? '三处一致（已关闭）' : '状态不一致，建议重跑 on / off'))
  return on
}

function enable() {
  const app = readJson(APP_JSON)
  app.plugins = app.plugins || {}
  app.plugins[PLUGIN_KEY] = { version: 'latest', provider: PLUGIN_PROVIDER }
  writeJson(APP_JSON, app)

  const page = readJson(PAGE_JSON)
  page.usingComponents = page.usingComponents || {}
  page.usingComponents[PLUGIN_KEY] = PLUGIN_COMPONENT
  writeJson(PAGE_JSON, page)

  flipConfig(true)
  console.log('已开启微信学生认证：')
  console.log('  · app.json            plugins.studentVerify = latest / ' + PLUGIN_PROVIDER)
  console.log('  · pages/user/verify   usingComponents 注册 plugin://studentVerify/verify')
  console.log('  · utils/config.js     WX_STUDENT_VERIFY = true')
  console.log('下一步：')
  console.log('  1) 小程序后台确认已添加「微信学生身份验证」插件（否则这一步会让工具编译失败）')
  console.log('  2) 后端配 WX_STUDENT_VERIFY=true（未拿到能力授权可先加 WX_STUDENT_VERIFY_MOCK=true 联调）')
  console.log('  3) 重新编译，认证页会出现「微信学生认证」通道')
}

function disable() {
  const app = readJson(APP_JSON)
  if (app.plugins) {
    delete app.plugins[PLUGIN_KEY]
    if (Object.keys(app.plugins).length === 0) delete app.plugins
  }
  writeJson(APP_JSON, app)

  const page = readJson(PAGE_JSON)
  if (page.usingComponents) {
    // 回退到占位组件而不是删掉映射：删掉之后 WXML 里的 <studentVerify> 找不到实现，
    // 在 lazyCodeLoading 下会报「组件不存在」，属于自找麻烦。
    page.usingComponents[PLUGIN_KEY] = STUB_COMPONENT
  }
  writeJson(PAGE_JSON, page)

  flipConfig(false)
  console.log('已关闭微信学生认证：插件声明移除、组件回退到本地占位、前端开关置 false。')
  console.log('认证页只会保留「上传学生证」通道，项目可正常编译。')
}

const action = (process.argv[2] || 'status').toLowerCase()
try {
  if (action === 'on') enable()
  else if (action === 'off') disable()
  else if (action === 'status') status()
  else {
    console.error('用法：node scripts/wx-student-plugin.js [on|off|status]')
    process.exit(1)
  }
} catch (e) {
  console.error('执行失败：' + e.message)
  process.exit(1)
}
