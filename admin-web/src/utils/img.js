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
