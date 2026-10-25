import axios from 'axios'
import { ElMessage } from 'element-plus'
import { BASE_URL } from '@/config/env'
import { resolve } from '@/utils/img'

/**
 * 请求层。复刻 miniprogram/utils/request.js 的三条约定：
 *   1. Authorization: Bearer <token>
 *   2. body.code === 0 才算成功，且直接把 data 剥出来给调用方
 *   3. 响应里含 /uploads/ 的字符串统一归一到当前 SERVER
 * 另外注意：所有雪花 ID 后端已序列化成字符串，这里严禁 Number(id) 转换。
 */
const service = axios.create({
  baseURL: BASE_URL,
  timeout: 15000
})

const TOKEN_KEY = 'admin_token'
export function getToken () {
  return localStorage.getItem(TOKEN_KEY) || ''
}
export function setToken (token) {
  localStorage.setItem(TOKEN_KEY, token)
}
export function clearToken () {
  localStorage.removeItem(TOKEN_KEY)
}

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

// 401 提示节流锁：并发请求一起 401 时只弹一次
let authTipLock = false
function handle401 (msg) {
  if (authTipLock) return
  authTipLock = true
  clearToken()
  ElMessage.error(msg || '登录已失效，请重新登录')
  setTimeout(() => {
    // 让出当前 tick 再跳转，避免在响应拦截里直接改路由导致告警
    window.location.href = '/login?redirect=' + encodeURIComponent(window.location.hash.replace(/^#/, '') || '/')
    setTimeout(() => { authTipLock = false }, 1000)
  }, 300)
}

function normalizeImages (v, depth = 0) {
  if (depth > 6 || v === null || v === undefined) return v
  if (typeof v === 'string') return v.indexOf('/uploads/') >= 0 ? resolve(v) : v
  if (Array.isArray(v)) {
    v.forEach((x, i) => { v[i] = normalizeImages(x, depth + 1) })
    return v
  }
  if (typeof v === 'object') {
    Object.keys(v).forEach((k) => { v[k] = normalizeImages(v[k], depth + 1) })
    return v
  }
  return v
}

service.interceptors.response.use(
  (response) => {
    const body = response.data
    if (!body || typeof body !== 'object') {
      ElMessage.error('接口返回格式异常')
      return Promise.reject(new Error('接口返回格式异常'))
    }
    if (body.code === 401) {
      handle401(body.msg)
      return Promise.reject(new Error(body.msg || '未登录'))
    }
    if (body.code === 403) {
      ElMessage.error(body.msg || '没有权限执行该操作')
      return Promise.reject(new Error(body.msg || '无权限'))
    }
    if (body.code !== 0) {
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return normalizeImages(body.data, 0)
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      handle401()
    } else if (status >= 500) {
      ElMessage.error('服务器开小差了，请稍后重试')
    } else if (status === 403) {
      ElMessage.error('没有权限执行该操作')
    } else if (error.code === 'ECONNABORTED' || String(error.message || '').includes('timeout')) {
      ElMessage.error('请求超时，请检查网络')
    } else {
      ElMessage.error(error?.response?.data?.msg || '网络异常，请检查后端服务是否已启动')
    }
    return Promise.reject(error)
  }
)

export default service
