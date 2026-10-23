const { mp, toast, PLATFORM, PLATFORM_NAME } = require('./util')
const { DEV_OPENID, USE_MOCK } = require('./config')
const http = require('./request')

let pending = null

function exchange(code) {
  // devOpenid 仅在后端 mock 模式生效，正式环境会被忽略
  return http.post('/auth/login', {
    code: code,
    platform: PLATFORM,
    devOpenid: DEV_OPENID
  }).then(function (res) {
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

/**
 * 剔除入参里的无效字段（undefined / null）。
 * 只过滤「要写入的入参」，不去清空已有缓存：
 * '' / 0 / false 都是合法业务值，必须原样保留，否则
 * 「未认证时把学校名清空」这类语义会被破坏。
 *
 * @param {Object} patch 待写入字段
 * @returns {Object} 只含有效值的字段集合
 */
function pickValid(patch) {
  var out = {}
  if (!patch) return out
  Object.keys(patch).forEach(function (k) {
    var v = patch[k]
    if (v === undefined || v === null) return
    out[k] = v
  })
  return out
}

/**
 * 更新本地用户缓存（globalData + storage）。
 *
 * 必须以「已有缓存」为基础做合并，而不是整体替换：
 * 调用方经常只传部分字段（如 id/schoolId/verifyStatus），整体替换会把缓存里已有的
 * schoolName 等字段冲掉，导致首页「选择学校」与认证学籍信息不一致。
 *
 * 合并前先剔除入参里的 null / undefined，避免后端字段缺失（未认证时 schoolName 为 null）
 * 把缓存中的有效值抹成空。
 *
 * @param {Object} user 需要写入的字段（可只传部分）
 * @returns {Object} 合并后的完整用户对象
 */
function updateUser(user) {
  var app = getApp()
  var cached = (app && app.globalData && app.globalData.user) || mp.getStorageSync('user') || {}
  var next = Object.assign({}, cached, pickValid(user))
  app.globalData.user = next
  mp.setStorageSync('user', next)
  return next
}

/**
 * 宿主平台说明（登录用）。
 * 注意：微信已废弃 getUserInfo / getUserProfile 的静默授权，取头像昵称必须由用户主动点击
 * `open-type="chooseAvatar"` 与 `input type="nickname"` 完成，见 pages/user/index。
 */
function platformInfo() {
  return { platform: PLATFORM, name: PLATFORM_NAME }
}

module.exports = {
  ensureLogin,
  ensureVerified,
  updateUser,
  doLogin,
  platformInfo
}
