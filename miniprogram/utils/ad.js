const http = require('./request')
const { mp } = require('./util')

/**
 * 广告素材的公共行为：上报点击 + 按 linkType 跳转。
 *
 * 抽出来的原因：投放位会越来越多（首页 / 功能区 / 表白墙 / 闲置 / 我的），
 * 每个页面各写一遍跳转分支，早晚会出现「首页能跳帖子、表白墙漏了商品」这种不一致。
 *
 * linkType 约定与后台素材表单一致：
 *   0 无跳转 / 1 帖子 / 2 商品 / 3 外链 / 4 小程序页面路径
 */
function reportClick (id) {
  if (!id) return
  // 失败静默：计数是运营指标，不能因为它弹错误提示或挡住跳转
  http.post('/ads/materials/' + id + '/click').catch(function () {})
}

/**
 * 打开素材指向的目标。
 *
 * 外链不能 navigateTo——小程序只能打开自身页面，所以复制到剪贴板让用户自己粘贴。
 */
function open (ad) {
  if (!ad) return
  const value = ad.linkValue || ''
  if (!value) return
  const type = ad.linkType || 0
  if (type === 1) {
    mp.navigateTo({ url: '/pages/post/detail?id=' + value })
  } else if (type === 2) {
    mp.navigateTo({ url: '/pages/goods/detail?id=' + value })
  } else if (type === 4) {
    mp.navigateTo({ url: value })
  } else if (type === 3) {
    mp.setClipboardData({ data: value })
  }
}

module.exports = { reportClick, open }
