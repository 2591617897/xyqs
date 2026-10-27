/**
 * 多词搜索的前端实现 —— 拆词规则必须和 backend 的 SearchKit 完全对齐，
 * 否则会出现「后端搜得到，本地过滤搜不到」这种自相矛盾的现象。
 *
 * 规则（与 SearchKit 一致）：
 *   - 分隔符：任意空白，含全角空格 U+3000（中文输入法下空格经常是全角的）
 *   - 去重：忽略大小写、保序
 *   - 限量 6 个词、单词最多 24 字（超了截断）
 *   - 丢弃只有 % _ 的词（否则本地过滤形同没写条件）
 *
 * 语义：词与词 AND（都要命中），同一词的多个字段之间 OR。
 */

var MAX_WORDS = 6
var MAX_WORD_LEN = 24
var SEP = /[\s\u3000]+/
var WILDCARD_ONLY = /^[%_]+$/

/** 拆词，返回字符串数组（永不返回 null） */
function words(keyword) {
  var raw = String(keyword == null ? '' : keyword).trim()
  if (!raw) return []
  var parts = raw.split(SEP)
  var out = []
  var seen = {}
  for (var i = 0; i < parts.length; i++) {
    var w = String(parts[i] || '').trim()
    if (!w || WILDCARD_ONLY.test(w)) continue
    // 按码点截断，别把 emoji 切成半个代理对
    var chars = Array.from ? Array.from(w) : w.split('')
    if (chars.length > MAX_WORD_LEN) w = chars.slice(0, MAX_WORD_LEN).join('')
    var key = w.toLowerCase()
    if (seen[key]) continue
    seen[key] = 1
    out.push(w)
    if (out.length >= MAX_WORDS) break
  }
  return out
}

/** 是否含有可用的搜索词 */
function hasWords(keyword) {
  return words(keyword).length > 0
}

/**
 * 单个字符串是否命中**所有**词。
 * @param {string} text 被检索文本
 * @param {string} keyword 原始输入
 */
function matchText(text, keyword) {
  var ws = words(keyword)
  if (!ws.length) return true
  var src = String(text == null ? '' : text).toLowerCase()
  for (var i = 0; i < ws.length; i++) {
    if (src.indexOf(ws[i].toLowerCase()) < 0) return false
  }
  return true
}

/**
 * 一行里只要**任意一个字段**能命中某个词，这个就算命中；但每个词都必须有字段命中。
 * 例：搜索「苹果 张三」，标题含苹果 + 昵称含张三 → 命中。
 *
 * @param {Object} row 数据行
 * @param {Array<string>} fields 参与匹配的字段名
 * @param {string} keyword 原始输入
 */
function matchFields(row, fields, keyword) {
  var ws = words(keyword)
  if (!ws.length) return true
  var r = row || {}
  for (var i = 0; i < ws.length; i++) {
    var w = ws[i].toLowerCase()
    var ok = false
    for (var j = 0; j < fields.length; j++) {
      var v = r[fields[j]]
      if (v != null && String(v).toLowerCase().indexOf(w) >= 0) {
        ok = true
        break
      }
    }
    if (!ok) return false
  }
  return true
}

/** 按多词条件过滤数组（matchFields 的数组版） */
function filterList(list, fields, keyword) {
  if (!words(keyword).length) return list || []
  return (list || []).filter(function (row) {
    return matchFields(row, fields, keyword)
  })
}

function escapeReg(s) {
  return String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * 把 text 按所有词切成 [{ t, hit }]，hit=true 的片段高亮。
 *
 * 用一条 alternation 正则一次扫完，比「按每个词各扫一遍」简单，也不会把
 * 已经切好的片段再切碎。词按长度降序排进正则，保证「苹果手机」优先于「苹果」，
 * 否则长词只会被标亮前两个字。
 *
 * @param {string} text
 * @param {string} keyword
 * @returns {Array<{t: string, hit: boolean}>}
 */
function splitHighlight(text, keyword) {
  var src = String(text == null ? '' : text)
  var plain = [{ t: src, hit: false }]
  var ws = words(keyword)
  if (!ws.length || !src) return plain

  ws.sort(function (a, b) { return b.length - a.length })
  var re = new RegExp(ws.map(escapeReg).join('|'), 'gi')
  var out = []
  var last = 0
  var m
  while ((m = re.exec(src)) !== null) {
    if (m[0].length === 0) { re.lastIndex++; continue }
    if (m.index > last) out.push({ t: src.slice(last, m.index), hit: false })
    out.push({ t: m[0], hit: true })
    last = m.index + m[0].length
  }
  if (last < src.length) out.push({ t: src.slice(last), hit: false })
  return out.length ? out : plain
}

module.exports = {
  MAX_WORDS: MAX_WORDS,
  MAX_WORD_LEN: MAX_WORD_LEN,
  words: words,
  hasWords: hasWords,
  matchText: matchText,
  matchFields: matchFields,
  filterList: filterList,
  splitHighlight: splitHighlight
}
