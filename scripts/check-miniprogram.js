/**
 * 小程序工程结构校验
 * 用法：node scripts/check-miniprogram.js
 *
 * 检查项（这些是「页面白屏 + usingComponents undefined」的高发原因）：
 *   1. app.json 里声明的每个页面，是否都有 .js / .wxml / .json
 *   2. 每个页面 json 是否可解析、是否显式声明 usingComponents
 *   3. tabBar 里的页面是否都在 pages 中声明
 *   4. 页面 js 是否会调用 Page()、wxml 是否为空
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

  report(pages.length)
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
