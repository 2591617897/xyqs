const { SERVER } = require('./config')

/**
 * 图片地址统一收敛到当前配置的服务器：
 *  - 相对路径 /uploads/...        → SERVER + 路径
 *  - 历史绝对地址（旧 IP/域名）   → 取路径部分，换成当前 SERVER
 *  - 小程序临时文件 / 外链        → 原样返回
 *
 * 这样换域名时只要改 utils/config.js，历史数据里的图片也能跟着切。
 */
function resolve(url) {
  if (!url || typeof url !== 'string') return ''
  const idx = url.indexOf('/uploads/')
  if (idx >= 0) {
    return SERVER + url.substring(idx)
  }
  return url
}

function resolveList(list) {
  if (!list || !list.length) return []
  return list.map(resolve)
}

/**
 * 图片网格档位（参考今日头条帖子排版）。
 *   1 张 → cols-1 单列大图
 *   2 / 4 张 → cols-2 两列（4 张即 2×2）
 *   3 张及以上 → cols-3 三列九宫格
 *
 * 放在这里而不是各页面自己算：首页三卡（热帖 / 帖子 / 树洞）与详情页共用同一口径，
 * 改规则只改这一处，不会出现「首页三列、详情页两列」的错位。
 */
function imgGridClass(n) {
  if (!n || n <= 1) return 'cols-1'
  if (n === 2 || n === 4) return 'cols-2'
  return 'cols-3'
}

/**
 * 图片显示模式：统一 aspectFill。
 *   单图 → 宽度水平铺满整行，高度由各页面 CSS 限定（超长竖图上下裁切，不把卡片撑高）
 *   多张 → 裁切成统一格子（九宫格必须正方形，否则格子高度会散）
 *
 * 为什么单图不用 widthFix：widthFix 下高度由原图比例决定，没法「限定高度」，
 * 一张超长竖图能把整张卡片撑到几千像素高。
 * 改用 aspectFill + 固定高度即可：宽度照旧铺满，超长部分裁掉（可裁内容），
 * 想看原图点一下就调 mp.previewImage 放大预览。
 *
 * 与 imgGridClass 配套使用，首页 / 详情页共用同一口径。
 */
function imgMode() {
  return 'aspectFill'
}

module.exports = {
  resolve: resolve,
  resolveList: resolveList,
  imgGridClass: imgGridClass,
  imgMode: imgMode
}
