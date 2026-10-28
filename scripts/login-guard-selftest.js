/**
 * 登录守卫自检：所有"写操作"（点赞 / 评论 / 发布 / 删除 / 关注）入口，
 * 在发请求前必须先确认登录，否则游客点了会拿到 401，且不知道发生了什么。
 *
 * 做法：按页面 js 里的方法切块，只要方法体内出现 http.post / http.del，
 * 就要求同一方法体内出现过 auth.ensureLogin() 或 auth.ensureVerified()。
 * 白名单用于确实不需要登录的写操作（例如埋点上报）。
 *
 * 用法：node scripts/login-guard-selftest.js
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..', 'miniprogram')

/** 确实不需要登录的写操作：方法名 → 原因 */
const WHITELIST = {
  'utils/request.js': [],
  'pages/auth/login.js': ['*'],
  'pages/auth/phone.js': ['*']
}

/** 收集所有页面/组件 js */
function walk(dir, base, out) {
  if (!fs.existsSync(dir)) return out
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name)
    const rel = path.join(base, name).replace(/\\/g, '/')
    if (fs.statSync(full).isDirectory()) {
      if (name === 'node_modules' || name === 'utils') continue
      walk(full, rel, out)
    } else if (name.endsWith('.js') && !name.endsWith('.json')) {
      out.push([rel, full])
    }
  }
  return out
}

const files = []
walk(path.join(ROOT, 'pages'), 'pages', files)
walk(path.join(ROOT, 'subpackages'), 'subpackages', files)
// 自定义 tab 栏也是交互入口
const tabBar = path.join(ROOT, 'custom-tab-bar/index.js')
if (fs.existsSync(tabBar)) files.push(['custom-tab-bar/index.js', tabBar])

const METHOD_RE = /^ {2}([A-Za-z0-9_]+):\s*function/
let pass = 0
let fail = 0
const problems = []

for (const [rel, full] of files) {
  const white = WHITELIST[rel]
  if (white && white.includes('*')) continue
  const src = fs.readFileSync(full, 'utf8')
  const lines = src.split('\n')

  // 切块：方法名 → 方法体
  const blocks = []
  let cur = null
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(METHOD_RE)
    if (m) {
      if (cur) blocks.push(cur)
      cur = { name: m[1], line: i + 1, body: [] }
    } else if (cur) {
      cur.body.push(lines[i])
    }
  }
  if (cur) blocks.push(cur)

  // 页面级守卫：onLoad / onShow 里 ensureLogin 的页面，整页交互都处于登录态
  const onLoad = blocks.find((b) => b.name === 'onLoad' || b.name === 'onShow')
  const pageGuarded = !!onLoad && /auth\.ensure(Login|Verified)\s*\(/.test(onLoad.body.join('\n'))

  for (const b of blocks) {
    const body = b.body.join('\n')
    const isWrite = /\bhttp\.(post|del|put)\s*\(/.test(body)
    if (!isWrite) continue
    // 有些方法是"先 ensureLogin().then(() => http.post(...))"，守卫在同一方法体内；
    // 有的是同步守卫 `if (!auth.guard()) return`（不便把方法体包成 Promise 的场景）
    const guarded = /auth\.(ensure(Login|Verified)|guard)\s*\(/.test(body)
      // 页面自有守卫：例如资料页 `if (!this.data.logged) { this.login(); return }`
      || /if\s*\(\s*!this\.data\.logged\b/.test(body)
    if (guarded) {
      pass++
      continue
    }
    // 页面级守卫：onLoad 里就要求登录的页面，后续所有写操作都已在登录态下
    if (pageGuarded) {
      pass++
      continue
    }
    fail++
    problems.push({ rel, name: b.name, line: b.line })
  }
}

console.log('\n写操作登录守卫检查')
console.log('─'.repeat(64))
if (problems.length) {
  console.log('以下写操作入口没有登录守卫（游客点击会拿到 401 且无任何提示）：\n')
  for (const p of problems) {
    console.log('  ❌ ' + p.rel + ':' + p.line + '  →  ' + p.name + '()')
  }
  console.log('')
} else {
  console.log('全部写操作入口都有登录守卫')
}
console.log('通过 ' + pass + ' / 缺失 ' + fail)
console.log('─'.repeat(64) + '\n')
process.exit(fail ? 1 : 0)
