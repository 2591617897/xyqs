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

module.exports = {
  resolve: resolve,
  resolveList: resolveList
}
