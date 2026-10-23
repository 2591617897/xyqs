const { mp, toast } = require('./util')
const { DEV_OPENID, USE_MOCK } = require('./config')
const http = require('./request')

let pending = null

function exchange(code) {
  // devOpenid 仅在后端 mock 模式生效，正式环境会被忽略
  return http.post('/auth/login', { code: code, devOpenid: DEV_OPENID }).then(function (res) {
    mp.setStorageSync('token', res.token)
    mp.setStorageSync('user', res.user)
    const app = getApp()
    app.globalData.token = res.token
    app.globalData.user = res.user
    return res.user
  })
}

function doLogin() {
  // 模拟数据模式：不依赖宿主登录能力，直接换成模拟用户
  if (USE_MOCK) {
    return exchange('mock_code')
  }
  return new Promise(function (resolve, reject) {
    mp.login({
      success: function (res) {
        exchange(res.code).then(resolve).catch(reject)
      },
      fail: function () {
        toast('登录失败，请重试')
        reject({ code: 500, msg: 'login fail' })
      }
    })
  })
}

/** 静默登录（已登录则直接返回缓存用户） */
function ensureLogin(force) {
  const app = getApp()
  const cached = app.globalData.user || mp.getStorageSync('user')
  if (!force && http.getToken() && cached) {
    app.globalData.user = cached
    return Promise.resolve(cached)
  }
  if (pending) return pending
  pending = doLogin().then(function (user) {
    pending = null
    return user
  }).catch(function (err) {
    pending = null
    throw err
  })
  return pending
}

/** 发布类操作：必须已完成校园身份认证 */
function ensureVerified() {
  return ensureLogin().then(function (user) {
    if (user && user.verifyStatus === 2) return user
    toast('请先完成校园身份认证')
    setTimeout(function () {
      mp.navigateTo({ url: '/pages/user/verify' })
    }, 600)
    return Promise.reject({ code: 403, msg: 'not verified' })
  })
}

function updateUser(user) {
  const app = getApp()
  app.globalData.user = user
  mp.setStorageSync('user', user)
}

module.exports = { ensureLogin, ensureVerified, updateUser, doLogin }
