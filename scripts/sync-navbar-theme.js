/**
 * 统一系统导航栏配色：把页面 json 里散落的白色导航栏
 *   navigationBarBackgroundColor: #FFFFFF / #F7F9FB / #006C49  →  #B8D6CC（浅绿）
 *   navigationBarTextStyle:       white                        →  black（浅底上必须用黑字）
 * 与 app.json 的 window 配置保持一致，并和页面渐变顶端同色（无缝衔接）。
 *
 * ⚠️ #B8D6CC 不是随便挑的：它是「渐变顶端 rgba(0,108,73,.28) 叠在白底上」的等效实色。
 *    navigationBarBackgroundColor 不支持 alpha，只能写实色，所以必须填这个等效值，
 *    否则导航栏与页面顶端之间会出现一条色阶断层。改 tokens.wxss 的 --grad-from 后要同步重算。
 *
 * 排除（不处理）：
 *   - pages/messages/index.json         : navigationStyle:custom，顶栏透明（已单独设 black）
 *   - subpackages/msg/group/*.json      : navigationStyle:custom 且顶栏是不透明白底，保持 black
 *
 * 用法：node scripts/sync-navbar-theme.js
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..', 'miniprogram')
// 渐变顶端 rgba(30,95,168,.28) 叠白底后的等效实色（navigationBarBackgroundColor 不支持 alpha）
const BRAND = '#C0D2E7'
const TEXT_STYLE = 'black'   // 浅蓝底上必须用黑字，白字不可读

// 需要统一配色的页面 json（当前在 app.json 之外单独覆盖了导航栏配色）
const TARGETS = [
  'pages/chat/index.json',
  'pages/legal/agreement.json',
  'pages/legal/disclaimer.json',
  'pages/legal/privacy.json',
  'pages/legal/rule.json',
  'pages/notice/index.json',
  'pages/report/report.json',
  'pages/user/follow/index.json',
  'pages/zone/index.json',
  'subpackages/me/dynamic/index.json',
  'subpackages/me/favorite/index.json',
  'subpackages/me/goods/mine.json',
  'subpackages/me/goods/sold.json',
  'subpackages/msg/notice/comment.json',
  'subpackages/msg/notice/fans.json',
  'subpackages/msg/notice/like.json',
  'subpackages/msg/notice/system.json'
]

let changed = 0
let skipped = 0

for (const rel of TARGETS) {
  const file = path.join(ROOT, rel)
  if (!fs.existsSync(file)) {
    console.log('SKIP(不存在) ' + rel)
    skipped++
    continue
  }
  let obj
  try {
    obj = JSON.parse(fs.readFileSync(file, 'utf8'))
  } catch (e) {
    console.log('FAIL(解析失败) ' + rel + ' -> ' + e.message)
    continue
  }

  // 只改「已经显式覆盖过」的页面；没覆盖的自然继承 app.json，无需写入
  let dirty = false
  if ('navigationBarBackgroundColor' in obj &&
      obj.navigationBarBackgroundColor.toUpperCase() !== BRAND.toUpperCase()) {
    obj.navigationBarBackgroundColor = BRAND
    dirty = true
  }
  if ('navigationBarTextStyle' in obj && obj.navigationBarTextStyle !== TEXT_STYLE) {
    obj.navigationBarTextStyle = TEXT_STYLE
    dirty = true
  }

  if (dirty) {
    fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n', 'utf8')
    console.log('CHANGED     ' + rel)
    changed++
  } else {
    console.log('NO-OP       ' + rel)
    skipped++
  }
}

console.log('\n完成：修改 ' + changed + ' 个，无需改动 ' + skipped + ' 个')
