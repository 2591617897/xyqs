/**
 * 图片地址归一。复刻 miniprogram/utils/img.js：
 * 库里存的是上传那一刻的绝对地址，换域名后会全部失效，
 * 这里统一取 '/uploads/' 之后的部分再拼当前 SERVER。
 */
import { SERVER } from '@/config/env'

export function resolve (url) {
  if (!url || typeof url !== 'string') return ''
  const idx = url.indexOf('/uploads/')
  if (idx >= 0) return SERVER + url.substring(idx)
  return url
}

/**
 * 这个地址浏览器真加载得出来吗？
 *
 * <p>库里的 {@code t_user.verify_img} 出现过 {@code wxfile://tmp_xxx.jpg} —— 小程序
 * {@code wx.chooseImage} 返回的本机临时文件路径，被原样提交进了数据库。它在浏览器里
 * 既不是 http 也不是 data URI，el-image 渲染出来就是一个「加载失败」的灰块，
 * 管理员完全不知道这是数据脏了还是网络挂了。
 *
 * <p>渲染前先判一次：不是 http(s) / data URI 就别丢给 img 标签，直接给出可读原因。
 */
export function isRenderable (url) {
  if (!url || typeof url !== 'string') return false
  return /^(https?:|data:image\/|\/\/|\/uploads\/)/.test(url)
}
