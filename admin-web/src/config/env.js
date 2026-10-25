/**
 * 全局地址配置。对齐小程序 miniprogram/utils/config.js 的 SERVER -> BASE_URL 派生写法：
 * 换环境只改 .env.* 里的 VITE_API_SERVER，其余全部由它派生。
 *
 * dev：VITE_API_SERVER 留空 -> BASE_URL='/api'，由 vite proxy 转到网关 8888（同源，无跨域）
 * prod：VITE_API_SERVER='https://campus-api.trkcwl.com' -> BASE_URL='https://campus-api.trkcwl.com/api'
 */
const rawServer = import.meta.env.VITE_API_SERVER || ''
// 去掉结尾多余的斜杠，避免拼出 https://x.com//api
export const SERVER = String(rawServer).replace(/\/$/, '')
export const APP_TITLE = import.meta.env.VITE_APP_TITLE || '校园聚管理台'

export const BASE_URL = SERVER + '/api'
export const ADMIN_API = BASE_URL + '/admin'
/** 广告素材等后台上传走 campus-admin 自带的上传接口 */
export const UPLOAD_URL = ADMIN_API + '/upload'
