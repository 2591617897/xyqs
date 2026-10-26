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

  pages.forEach(function (p) {
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

  report(pages.length)
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
  ;['pages', 'components', 'utils'].forEach(function (dir) {
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

function report(pageCount) {
  console.log('')
  console.log('页面数：' + (pageCount || 0))
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
