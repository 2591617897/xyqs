/**
 * 小程序工程结构校验
 * 用法：node scripts/check-miniprogram.js
 *
 * 检查项（这些是「页面白屏 + usingComponents undefined」的高发原因）：
 *   1. app.json 里声明的每个页面，是否都有 .js / .wxml / .json
 *   2. 每个页面 json 是否可解析、是否显式声明 usingComponents
 *   3. tabBar 里的页面是否都在 pages 中声明
 *   4. 页面 js 是否会调用 Page()、wxml 是否为空
 *   5. 按需注入（lazyCodeLoading）是否开启；开启后 wxml 用到的自定义标签是否都在
 *      对应 json 的 usingComponents 里声明（漏声明会「静默不渲染」，比白屏更难查）
 *   6. usingComponents 里的路径是否真的能解析到文件（绝对路径 / 开头按小程序根目录，
 *      否则按当前 json 所在目录）。历史 bug：pages/goods/detail.json 写了
 *      "components/promote-sheet/promote-sheet"，被解析成
 *      pages/goods/components/...，开发者工具直接「模拟器启动失败」
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..', 'miniprogram')
const errors = []
const warnings = []

function readJson(file) {
  const raw = fs.readFileSync(file, 'utf8')
  // 去掉 UTF-8 BOM，BOM 会导致部分工具解析 json 失败
  const clean = raw.replace(/^\uFEFF/, '')
  if (clean !== raw) warnings.push('文件含 BOM，建议另存为 UTF-8 无 BOM：' + rel(file))
  return JSON.parse(clean)
}

function rel(f) {
  return path.relative(ROOT, f).replace(/\\/g, '/')
}

function exists(f) {
  return fs.existsSync(f)
}

/** 内置组件（含带 - 的），用于从 wxml 里筛出真正的自定义标签 */
const BUILTIN_TAGS = new Set([
  'view', 'text', 'image', 'button', 'input', 'textarea', 'navigator', 'block', 'template',
  'slot', 'icon', 'label', 'form', 'picker', 'switch', 'slider', 'progress', 'rich-text',
  'scroll-view', 'swiper', 'swiper-item', 'checkbox', 'checkbox-group', 'radio', 'radio-group',
  'picker-view', 'picker-view-column', 'movable-area', 'movable-view', 'cover-view', 'cover-image',
  'web-view', 'video', 'audio', 'canvas', 'map', 'camera', 'image-viewer', 'open-data', 'ad',
  'official-account', 'functional-page-navigator', 'live-player', 'live-pusher', 'editor',
  'match-media', 'page-container', 'page-meta', 'navigation-bar', 'root-portal', 'share-element',
  'keyboard-accessory', 'grid-view', 'list-view', 'sticky-header', 'sticky-section',
  'snapshot', 'channel-live', 'channel-video', 'voip-room', 'wxs', 'include', 'import'
])

/** 扫 wxml 里用到的自定义标签（去掉注释与 wxs 代码块，避免误判） */
function collectCustomTags(wxmlFile) {
  const src = fs.readFileSync(wxmlFile, 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<wxs[\s\S]*?<\/wxs>/g, '')
  const tags = new Set()
  const re = /<([a-zA-Z][\w-]*)/g
  let m
  while ((m = re.exec(src))) {
    const t = m[1]
    if (t.indexOf('-') > 0 && !BUILTIN_TAGS.has(t)) tags.add(t)
  }
  return Array.from(tags)
}

/**
 * 按需注入下，wxml 用到但 json 未声明的自定义标签会被当成未知标签：
 * 不渲染、不报错、控制台也没有明显提示 —— 所以必须静态查出来。
 */
function checkRequiredComponents(app, pages) {
  const globalUC = app.usingComponents || {}

  const checkUnit = function (base) {
    const wxml = base + '.wxml'
    if (!exists(wxml)) return
    const used = collectCustomTags(wxml)
    if (!used.length) return
    const json = base + '.json'
    let declared = {}
    if (exists(json)) {
      try {
        declared = readJson(json).usingComponents || {}
      } catch (e) {
        return // 解析失败已在上文报错
      }
    }
    const all = Object.assign({}, globalUC, declared)
    const missing = used.filter(function (t) { return !all[t] })
    if (missing.length) {
      errors.push('按需注入会静默不渲染（wxml 用了但 json 未声明）：' + rel(wxml) + ' → ' + missing.join(', '))
    }
  }

  pages.forEach(function (p) { checkUnit(path.join(ROOT, p)) })
  ;(app.subpackages || []).concat(app.subPackages || []).forEach(function (sp) {
    ;(sp.pages || []).forEach(function (p) {
      checkUnit(path.join(ROOT, sp.root, p))
    })
  })
  const compDir = path.join(ROOT, 'components')
  if (exists(compDir)) {
    fs.readdirSync(compDir).forEach(function (d) {
      const base = path.join(compDir, d, d)
      if (exists(base + '.js')) checkUnit(base)
    })
  }
  if (app.tabBar && app.tabBar.custom) {
    checkUnit(path.join(ROOT, 'custom-tab-bar', 'index'))
  }
}

/**
 * 校验 usingComponents 的路径能否落到真实文件。
 * 小程序规则：以 / 开头 = 相对小程序根目录；否则 = 相对该 json 所在目录。
 * 末尾可以不带后缀，也可以指向目录里的 index。
 */
function checkComponentPaths(app, pages) {
  const resolveTarget = function (dir, p) {
    const norm = p.replace(/\\/g, '/')
    const base = norm.startsWith('/') ? path.join(ROOT, norm) : path.join(dir, norm)
    return [
      base + '.js',
      path.join(base, 'index.js')
    ].some(exists)
  }

  const checkUnit = function (jsonFile, label) {
    if (!exists(jsonFile)) return
    let cfg
    try {
      cfg = readJson(jsonFile)
    } catch (e) {
      return // 解析失败已在上文报错
    }
    const uc = cfg.usingComponents || {}
    const dir = path.dirname(jsonFile)
    Object.keys(uc).forEach(function (tag) {
      const p = uc[tag]
      if (typeof p !== 'string' || !p) return
      // 插件 / npm 走 node_modules，跳过
      if (p.startsWith('plugin://') || p.includes('miniprogram_npm')) return
      if (!resolveTarget(dir, p)) {
        errors.push('usingComponents 路径解析不到组件：' + label + ' → ' + tag + ': "' + p
          + '"（建议写成 "/components/' + tag + '/' + tag + '"）')
      }
    })
  }

  checkUnit(path.join(ROOT, 'app.json'), 'app.json')
  pages.forEach(function (p) { checkUnit(path.join(ROOT, p + '.json'), p + '.json') })
  ;(app.subpackages || []).concat(app.subPackages || []).forEach(function (sp) {
    ;(sp.pages || []).forEach(function (p) {
      const full = sp.root + '/' + p
      checkUnit(path.join(ROOT, full + '.json'), full + '.json')
    })
  })

  const compDir = path.join(ROOT, 'components')
  if (exists(compDir)) {
    fs.readdirSync(compDir).forEach(function (d) {
      checkUnit(path.join(compDir, d, d + '.json'), 'components/' + d)
    })
  }
  if (app.tabBar && app.tabBar.custom) {
    checkUnit(path.join(ROOT, 'custom-tab-bar', 'index.json'), 'custom-tab-bar/index.json')
  }
}

function main() {
  const appJsonPath = path.join(ROOT, 'app.json')
  if (!exists(appJsonPath)) {
    errors.push('缺少 app.json')
    return report()
  }

  let app
  try {
    app = readJson(appJsonPath)
  } catch (e) {
    errors.push('app.json 解析失败：' + e.message)
    return report()
  }

  const pages = app.pages || []
  if (!pages.length) errors.push('app.json 的 pages 为空')

  // 分包页面（subPackages）同样要过一遍：缺 js/wxml/json、json 没写 usingComponents、
  // js 里没有 Page() 都会白屏，主包分包一视同仁。
  // root 里可能带 'subpackages/me' 这种多级路径，拼出来的 base 直接就是页面绝对基准。
  const subPages = []
  ;(app.subpackages || []).concat(app.subPackages || []).forEach(function (sp) {
    ;(sp.pages || []).forEach(function (p) { subPages.push(sp.root + '/' + p) })
  })
  const pageList = pages.concat(subPages)

  pageList.forEach(function (p) {
    const base = path.join(ROOT, p)
    const js = base + '.js'
    const wxml = base + '.wxml'
    const json = base + '.json'

    if (!exists(js)) errors.push('缺少页面 js：' + p + '.js')
    if (!exists(wxml)) errors.push('缺少页面 wxml：' + p + '.wxml')
    if (!exists(json)) {
      errors.push('缺少页面 json：' + p + '.json  ← 会导致 usingComponents undefined')
    } else {
      try {
        const cfg = readJson(json)
        if (!Object.prototype.hasOwnProperty.call(cfg, 'usingComponents')) {
          warnings.push('页面 json 未显式声明 usingComponents：' + p + '.json')
        }
      } catch (e) {
        errors.push('页面 json 解析失败：' + p + '.json → ' + e.message)
      }
    }

    if (exists(js)) {
      const code = fs.readFileSync(js, 'utf8')
      if (!/Page\s*\(/.test(code)) errors.push('页面 js 未调用 Page()：' + p + '.js')
    }
    if (exists(wxml)) {
      const code = fs.readFileSync(wxml, 'utf8').trim()
      if (!code) warnings.push('页面 wxml 为空：' + p + '.wxml')
    }
  })

  checkRequestUsage()
  checkComponentPaths(app, pages)
  checkWxmlExpressions()
  checkImageUploaded()

  // tabBar 校验
  const tabBar = app.tabBar
  if (tabBar && tabBar.list) {
    if (tabBar.list.length < 2 || tabBar.list.length > 5) {
      errors.push('tabBar.list 数量必须为 2~5，当前 ' + tabBar.list.length)
    }
    tabBar.list.forEach(function (item) {
      if (pages.indexOf(item.pagePath) < 0) {
        errors.push('tabBar 页面未在 pages 中声明：' + item.pagePath)
      }
    })
  }

  // 其他必要文件
  ;['app.js', 'app.wxss'].forEach(function (f) {
    if (!exists(path.join(ROOT, f))) errors.push('缺少根文件：' + f)
  })

  // sitemap
  if (app.sitemapLocation) {
    const s = path.join(ROOT, app.sitemapLocation)
    if (!exists(s)) errors.push('sitemapLocation 指向的文件不存在：' + app.sitemapLocation)
    else {
      try {
        readJson(s)
      } catch (e) {
        errors.push('sitemap.json 解析失败：' + e.message)
      }
    }
  }

  // 自定义 tabBar
  if (app.tabBar && app.tabBar.custom) {
    const dir = path.join(ROOT, 'custom-tab-bar')
    ;['index.js', 'index.json', 'index.wxml', 'index.wxss'].forEach(function (f) {
      if (!exists(path.join(dir, f))) {
        errors.push('自定义 tabBar 缺少文件：custom-tab-bar/' + f)
      }
    })
    const cj = path.join(dir, 'index.json')
    if (exists(cj)) {
      try {
        if (!readJson(cj).component) {
          errors.push('custom-tab-bar/index.json 缺少 "component": true')
        }
      } catch (e) {
        errors.push('custom-tab-bar/index.json 解析失败：' + e.message)
      }
    }
  }

  // 按需注入：既检查有没有开，也检查开了之后组件声明齐不齐
  if (app.lazyCodeLoading !== 'requiredComponents') {
    warnings.push('app.json 未开启 "lazyCodeLoading": "requiredComponents"（开发者工具「代码质量」的「启用组件按需注入」会判未通过）')
  } else {
    checkRequiredComponents(app, pages)
  }

  // preloadRule（分包预下载）校验：键必须是主包页面，packages 必须是已注册分包的 root 或 name
  const spList = (app.subpackages || []).concat(app.subPackages || [])
  const spKeys = spList.map(function (sp) { return sp.root })
    .concat(spList.map(function (sp) { return sp.name }).filter(Boolean))
  if (app.preloadRule && !spKeys.length) {
    warnings.push('app.json 配了 preloadRule 但没有任何分包')
  }
  Object.keys(app.preloadRule || {}).forEach(function (key) {
    if (pages.indexOf(key) < 0) {
      errors.push('preloadRule 的键必须是主包页面路径：' + key)
    }
    ;((app.preloadRule[key] || {}).packages || []).forEach(function (pk) {
      if (spKeys.indexOf(pk) < 0) {
        errors.push('preloadRule 指向未注册的分包：' + pk + '（应写分包 root 或 name）')
      }
    })
  })

  report(pages.length, subPages.length)
}

/**
 * utils/request.js 导出的是对象 { request, get, post, put, del }，不是函数。
 * 历史 bug：举报页写成 `http({ url, method, data })`，运行期抛
 * `TypeError: http is not a function`，请求根本发不出去（页面看起来像「没权限」）。
 * 这里把所有 .js 扫一遍，禁止把 request 模块本身当函数调用。
 *
 * ⚠️ 必须先剔注释再匹配：注释和说明文档里会引用 `http({...})` 这个错误写法，
 *    不剔掉就会变成假阳性——和 scripts/security-baseline-check.js 是同一个坑。
 */
function checkRequestUsage() {
  const files = []
  ;['pages', 'components', 'utils', 'subpackages'].forEach(function (dir) {
    walk(path.join(ROOT, dir), files)
  })

  files.forEach(function (file) {
    if (path.basename(file) === 'request.js') return // 模块自身不参与
    let code = fs.readFileSync(file, 'utf8')
    code = code.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1')
    const rq = /require\(\s*['"][^'"]*utils\/request['"]\s*\)/
    if (!rq.test(code)) return

    // const http = require('.../utils/request')
    const m = code.match(/(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*require\(\s*['"][^'"]*utils\/request['"]\s*\)/)
    if (!m) return
    const alias = m[1]
    // alias 后面紧跟 ( 就是「把模块当函数调用」；alias.get( 有小数点，不会误伤
    const bad = new RegExp('(?<![.\\w$])' + alias.replace(/\$/g, '\\$') + '\\s*\\(')
    if (bad.test(code)) {
      errors.push('utils/request 导出的是对象，不能用 ' + alias + '({...}) 调用 → '
        + rel(file) + '（应改为 ' + alias + '.get/post/put/del(...)）')
    }
  })
}

/**
 * 选图必须真上传。
 *
 * <p>起因：pages/user/verify.js 把 wx.chooseImage 返回的 `wxfile://tmp_xxx.jpg`
 * （本机临时文件路径）原样提交给 /user/verify，写进 t_user.verify_img 之后，
 * 后台「用户详情 → 认证材料」在浏览器里渲染这串地址，显示「加载失败」——
 * 材料从来没上过服务器，审核员不知道这数据是坏的不是网挂的。
 *
 * <p>规则很简单：**页面里出现 chooseImage 就必须 require utils/upload**，
 * 且 chooseImage 的回调里必须调用 uploadImage/uploadImages/uploadVideo。
 * 这条能挡住「新增上传点时忘了往服务端传」这个最容易复发的错误。
 */
function checkImageUploaded() {
  const files = []
  walk(ROOT, files)
  files.forEach(function (file) {
    let code
    try { code = fs.readFileSync(file, 'utf8') } catch (e) { return }
    if (!/chooseImage|chooseMedia|chooseVideo|chooseMessageFile/.test(code)) return
    const r = rel(file)
    if (!/require\(\s*['"][^'"]*utils\/upload['"]\s*\)/.test(code)) {
      errors.push('选图后没 require utils/upload（本地 wxfile:// 路径不能直接提交）→ ' + r)
      return
    }
    const body = code.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1')
    // 只验证「引用到了 upload 的上传方法」：有的页面先把方法取成别名变量
    // （如 const uploader = type === 'video' ? upload.uploadVideo : upload.uploadImage），
    // 调的是 uploader(path)，强行要求 upload.xxx( 会误报。
    if (!/upload\.(uploadImage|uploadImages|uploadVideo)\b/.test(body)) {
      errors.push('选图后没调用 upload.uploadImage/Images/Video（会拿到 wxfile:// 临时路径）→ ' + r)
    }
  })
}

function walk(dir, out) {
  if (!fs.existsSync(dir)) return
  fs.readdirSync(dir, { withFileTypes: true }).forEach(function (d) {
    const full = path.join(dir, d.name)
    if (d.isDirectory()) {
      walk(full, out)
    } else if (d.name.endsWith('.js')) {
      out.push(full)
    }
  })
}

/**
 * WXML 表达式语法校验（把白屏挡在开发者工具之外）。
 *
 * 起因：空态文案写成了四层嵌套三目，左括号 3 个、右括号 4 个，
 * 编译期直接报 Bad value with message: error at token '}' → 整页白屏。
 * 这类错误不会被任何「文件是否存在」的检查发现，只能真去解析表达式。
 *
 * 做法：先把 HTML 注释抹成空格（保住行号），再逐个 {{ }} 取出表达式，
 * 剔除字符串字面量后核对圆括号 / 方括号是否配平。
 */
function checkWxmlExpressions() {
  const files = []
  const walk = function (dir, depth) {
    if (depth > 4 || !exists(dir)) return
    fs.readdirSync(dir).forEach(function (name) {
      if (name === 'node_modules' || name === 'miniprogram_npm') return
      const full = path.join(dir, name)
      let st
      try { st = fs.statSync(full) } catch (e) { return }
      if (st.isDirectory()) return walk(full, depth + 1)
      if (/\.wxml$/i.test(name)) files.push(full)
    })
  }
  walk(ROOT, 0)

  files.forEach(function (file) {
    let src = fs.readFileSync(file, 'utf8')
    // 注释抹成等长空格：行号不变，且注释里的 {{ }} 不会被误判
    src = src.replace(/<!--[\s\S]*?-->/g, function (m) {
      return m.replace(/[^\n]/g, ' ')
    })

    let i = 0
    while (i < src.length) {
      const open = src.indexOf('{{', i)
      if (open < 0) break
      const line = src.slice(0, open).split('\n').length
      const end = matchExprEnd(src, open)
      if (end < 0) {
        errors.push('{{ 未闭合：' + rel(file) + ' 第 ' + line + ' 行')
        return
      }
      const bad = balanceOf(src.slice(open + 2, end))
      if (bad) {
        errors.push('WXML 表达式括号不配平（会整页白屏）：' + rel(file) + ' 第 ' + line + ' 行 → ' + bad)
      }
      i = end + 2
    }
  })
}

/** 从 {{ 位置起找配对的 }}，跳过字符串字面量内部的 } */
function matchExprEnd(src, open) {
  let j = open + 2
  let quote = null
  while (j < src.length) {
    const ch = src[j]
    if (quote) {
      if (ch === '\\') { j += 2; continue }
      if (ch === quote) quote = null
      j++
      continue
    }
    if (ch === "'" || ch === '"') { quote = ch; j++; continue }
    if (ch === '}' && src[j + 1] === '}') return j
    j++
  }
  return -1
}

/** 剔除字符串字面量后核对括号；返回 null 表示正常，否则返回错误描述 */
function balanceOf(expr) {
  const plain = expr
    .replace(/'(?:[^'\\]|\\.)*'/g, "''")
    .replace(/"(?:[^"\\]|\\.)*"/g, '""')
  const pairs = [['(', ')'], ['[', ']']]
  for (let k = 0; k < pairs.length; k++) {
    const l = pairs[k][0]
    const r = pairs[k][1]
    let depth = 0
    let over = false
    for (let i = 0; i < plain.length; i++) {
      if (plain[i] === l) depth++
      else if (plain[i] === r) { depth--; if (depth < 0) { over = true; break } }
    }
    if (over) return '出现多余的 ' + r
    if (depth !== 0) return '左 ' + l + ' 比右 ' + r + ' 多 ' + depth + ' 个'
  }
  return null
}

function report(pageCount, subPageCount) {
  console.log('')
  console.log('页面数：' + (pageCount || 0) + '（主包） + ' + (subPageCount || 0) + '（分包）')
  if (warnings.length) {
    console.log('\n警告（' + warnings.length + '）')
    warnings.forEach(function (w) {
      console.log('  ! ' + w)
    })
  }
  if (errors.length) {
    console.log('\n错误（' + errors.length + '）')
    errors.forEach(function (e) {
      console.log('  x ' + e)
    })
    console.log('\n结果：不通过')
    process.exit(1)
  }
  console.log('\n结果：通过，页面配置完整')
}

main()
