/**
 * 主题换色：品牌绿 → 蓝系（与预览页 预览-背景渐变与动画.html 的蓝系取值一致）
 *
 * 目标主色 #1E5FA8（＝预览页里的 rgb(30,95,168)），白字对比度 6.45:1，满足 WCAG AA。
 * 导航栏等效实色 #C0D2E7 ＝「渐变顶端 rgba(30,95,168,.28) 叠在白底上」的实色
 * （navigationBarBackgroundColor 不支持 alpha，只能写实色）。
 *
 * 只替换「品牌/主题色」；语义色（danger 红 / warning 琥珀 / success 绿 / like 粉）保持不动，
 * 它们是功能性颜色，改成蓝色会丢失语义。
 *
 * 用法：node scripts/switch-theme-blue.js
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..', 'miniprogram')
const EXTS = ['.wxss', '.js', '.wxml', '.json']

// [原色, 新色]，大小写不敏感
const MAP = [
  ['#006C49', '#1E5FA8'],   // 品牌主绿 → 品牌主蓝（100+ 处硬编码与 var() 兜底）
  ['#0e9a60', '#1E5FA8'],   // switch 开关绿 → 主蓝
  ['#0f9f5f', '#1E5FA8'],   // 首页 banner 渐变暗端 / 文字绿 → 主蓝
  ['#1cb877', '#3B8FD4'],   // 首页 banner 渐变亮端 → 亮蓝
  ['#B8D6CC', '#C0D2E7'],   // 导航栏浅绿等效实色 → 浅蓝等效实色
  // 品牌色阶（与主色同源，必须一起换，否则浅底/按下态/深字色仍是绿的）
  ['#E8F5EF', '#E8F0FA'],   // brand-50
  ['#C6E8D8', '#C7DDF3'],   // brand-100
  ['#93D3B9', '#93C0E8'],   // brand-200
  ['#5FB894', '#5B97D1'],   // brand-300
  ['#005A3D', '#17508F'],   // brand-600（按下态）
  ['#00452F', '#123F70']    // brand-700（浅底上的深色文字）
]

// rgba() 形式的品牌绿：上一轮只替换了 hex，漏掉了 rgba(0,108,73,α) 这类「带透明度的品牌色」
// —— 阴影、浅底、描边大量使用它，不换就会在全站留下 50 处绿色。
// 处理方式：只换 RGB 三元组，**保留原有 alpha**，否则浓淡关系会全乱。
const RGBA_MAP = [
  [/rgba\(\s*0\s*,\s*108\s*,\s*73\s*,/gi, 'rgba(30, 95, 168,'],  // 品牌主绿 → 主蓝
  [/rgba\(\s*14\s*,\s*154\s*,\s*96\s*,/gi, 'rgba(30, 95, 168,']  // #0e9a60（switch 绿）→ 主蓝
]

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name)
    const st = fs.statSync(p)
    if (st.isDirectory()) walk(p, out)
    else if (EXTS.includes(path.extname(name))) out.push(p)
  }
  return out
}

const files = walk(ROOT)
let fileChanged = 0
const hits = {}

for (const file of files) {
  let src = fs.readFileSync(file, 'utf8')
  let dirty = false
  for (const [from, to] of MAP) {
    const re = new RegExp(from.replace(/[#]/g, '#'), 'gi')
    const n = (src.match(re) || []).length
    if (n > 0) {
      src = src.replace(re, to)
      hits[from] = (hits[from] || 0) + n
      dirty = true
    }
  }
  for (const [re, to] of RGBA_MAP) {
    const n = (src.match(re) || []).length
    if (n > 0) {
      src = src.replace(re, to)
      hits[re.source] = (hits[re.source] || 0) + n
      dirty = true
    }
  }
  if (dirty) {
    fs.writeFileSync(file, src, 'utf8')
    fileChanged++
    console.log('CHANGED  ' + path.relative(ROOT, file))
  }
}

console.log('\n替换统计：')
for (const [from, n] of Object.entries(hits)) {
  const hex = MAP.find(m => m[0] === from)
  const rgb = RGBA_MAP.find(m => m[0].source === from)
  const to = hex ? hex[1] : (rgb ? rgb[1] : '?')
  console.log('  ' + from + ' → ' + to + ' ：' + n + ' 处')
}
console.log('\n共修改 ' + fileChanged + ' 个文件')
