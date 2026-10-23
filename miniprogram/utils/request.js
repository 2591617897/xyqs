const { BASE_URL, USE_MOCK, TIMEOUT } = require('./config')
const { mp, toast } = require('./util')
const mock = require('./mock')

/** 401 提示节流，避免并发请求弹多次 */
let authTipLock = false

function getToken() {
  return mp.getStorageSync('token') || ''
}

function cleanLogin() {
  mp.removeStorageSync('token')
  mp.removeStorageSync('user')
  const app = getApp()
  if (app && app.globalData) {
    app.globalData.token = ''
    app.globalData.user = null
  }
  if (USE_MOCK) mock.logout()
}

function authExpired(silent) {
  cleanLogin()
  if (silent || authTipLock) return
  authTipLock = true
  toast('登录已失效，请到「我的」重新登录')
  setTimeout(function () {
    authTipLock = false
  }, 1500)
}

/** 统一错误对象：{code, msg, network} */
function makeError(code, msg, network) {
  return { code: code, msg: msg, network: !!network }
}

function netTip(err) {
  const msg = err && err.errMsg ? String(err.errMsg) : ''
  if (msg.indexOf('timeout') >= 0) return '请求超时，请稍后重试'
  if (msg.indexOf('domain') >= 0 || msg.indexOf('not in domain list') >= 0) {
    return '域名未配置：请在开发者工具勾选「不校验合法域名」'
  }
  return '网络异常，请确认后端服务已启动'
}

/**
 * 模拟数据分支
 */
function mockRequest(url, method, data, silent, loading) {
  return new Promise(function (resolve, reject) {
    const delay = 160 + Math.round(Math.random() * 220)
    setTimeout(function () {
      let body
      try {
        body = mock.handle(method, url, data)
      } catch (e) {
        body = { code: 500, msg: '模拟数据异常' }
      }
      if (!body || typeof body.code !== 'number') body = { code: 500, msg: '模拟数据异常' }

      if (body.code === 401) {
        authExpired(silent)
        reject(makeError(401, '未登录'))
        return
      }
      if (body.code === 0) {
        resolve(body.data)
        return
      }
      if (!silent) toast(body.msg || '请求失败')
      reject(makeError(body.code, body.msg || '请求失败'))
    }, delay)
  })
}

/**
 * 真实请求
 * options: { url, method, data, silent, loading, timeout, retry }
 *   silent  不弹错误提示（页面自行处理）
 *   loading true / '提交中' → 显示 loading 遮罩
 */
function request(options) {
  const url = options.url
  const method = options.method || 'GET'
  const data = options.data
  const silent = !!options.silent
  const loading = options.loading
  const timeout = options.timeout || TIMEOUT

  if (loading) {
    mp.showLoading({
      title: typeof loading === 'string' ? loading : '加载中',
      mask: true
    })
  }
  const finish = function () {
    if (loading) mp.hideLoading()
  }

  if (USE_MOCK) {
    const p = mockRequest(url, method, data, silent, loading)
    p.then(finish).catch(finish)
    return p
  }

  const header = { 'content-type': 'application/json' }
  const token = getToken()
  if (token) header.Authorization = 'Bearer ' + token

  return new Promise(function (resolve, reject) {
    mp.request({
      url: BASE_URL + url,
      method: method,
      data: data,
      header: header,
      timeout: timeout,
      success: function (res) {
        finish()
        const body = res.data || {}

        if (res.statusCode === 401 || body.code === 401) {
          authExpired(silent)
          reject(makeError(401, '未登录'))
          return
        }
        if (res.statusCode === 403) {
          if (!silent) toast(body.msg || '没有权限执行该操作')
          reject(makeError(403, body.msg || '无权限'))
          return
        }
        if (res.statusCode >= 500) {
          if (!silent) toast(body.msg || '服务器开小差了，请稍后重试')
          reject(makeError(res.statusCode, body.msg || '服务器异常'))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        if (body.code === undefined) {
          // 网关或未按约定返回（例如 404 页面）
          if (!silent) toast('接口不存在或服务未启动')
          reject(makeError(res.statusCode || 500, '响应格式异常'))
          return
        }
        if (!silent) toast(body.msg || '请求失败')
        reject(makeError(body.code, body.msg || '请求失败'))
      },
      fail: function (err) {
        finish()
        if (!silent) toast(netTip(err))
        reject(makeError(-1, netTip(err), true))
      }
    })
  })
}

module.exports = {
  request: request,
  get: (url, data, opt) => request(Object.assign({ url, method: 'GET', data }, opt || {})),
  post: (url, data, opt) => request(Object.assign({ url, method: 'POST', data }, opt || {})),
  put: (url, data, opt) => request(Object.assign({ url, method: 'PUT', data }, opt || {})),
  del: (url, data, opt) => request(Object.assign({ url, method: 'DELETE', data }, opt || {})),
  getToken,
  cleanLogin,
  USE_MOCK,
  BASE_URL
}
