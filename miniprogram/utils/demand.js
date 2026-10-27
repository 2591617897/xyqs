/**
 * 校园需求广场（求物 / 求帮忙）前端公共口径。
 *
 * 只放「前端必须自己知道」的东西：大类、状态文案、展示加工。
 * 二级分类一律走 GET /demands/categories —— 后端 CATEGORIES 是唯一数据源，
 * 前端不抄一份分类名，避免后台加分类后前端不显示（闲置那边已经踩过一次）。
 */

const { timeAgo } = require('./util')
const { resolveList } = require('./img')

/** 需求大类：与后端 Demand.TYPE_* 对齐 */
const TYPES = [
  { key: 1, label: '求物', icon: '🎁', desc: '想收某个东西' },
  { key: 2, label: '求帮忙', icon: '🙋', desc: '想找人搭把手' }
]

/** 状态文案：与后端 Demand.STATUS_* 对齐 */
const STATUS_TEXT = {
  0: '已下架',
  1: '进行中',
  2: '已完成'
}

/** 回应状态：与后端 DemandReply.STATUS_* 对齐 */
const REPLY_STATUS_TEXT = {
  0: '未读',
  1: '已读',
  2: '已忽略'
}

function typeMeta(type) {
  return TYPES.filter(function (t) { return t.key === type })[0] || TYPES[0]
}

function statusText(status) {
  return STATUS_TEXT[status] || '进行中'
}

/** 预算：为空显示「面议」，不显示「¥0」 */
function budgetText(budget) {
  if (budget === null || budget === undefined || budget === '') return '面议'
  const n = Number(budget)
  if (isNaN(n)) return '面议'
  return '¥' + (n % 1 === 0 ? String(n) : n.toFixed(2))
}

/**
 * 列表项加工。
 * 与 pages/goods/list 的 decorate 同构：把「wxml 算不出来的东西」在 js 里算好。
 */
function decorate(item) {
  const meta = typeMeta(item.type)
  item.typeLabel = meta.label
  item.typeIcon = meta.icon
  item.statusText = statusText(item.status)
  item.budgetText = budgetText(item.budget)
  item.timeText = timeAgo(item.createdAt)
  item.replyText = item.replyCount > 0 ? String(item.replyCount) + ' 人回应' : '等你来接'
  item.descText = String(item.description || '').replace(/\s+/g, ' ').trim()
  item.imageList = resolveList(item.imageList)
  return item
}

module.exports = {
  TYPES: TYPES,
  STATUS_TEXT: STATUS_TEXT,
  REPLY_STATUS_TEXT: REPLY_STATUS_TEXT,
  typeMeta: typeMeta,
  statusText: statusText,
  budgetText: budgetText,
  decorate: decorate
}
