const { mp, toast, PLATFORM, PLATFORM_NAME } = require('./util')
const { DEV_OPENID, DEV_LOGIN, USE_MOCK } = require('./config')
const http = require('./request')

let pending = null

/** 本次运行里用户是否拒绝过登录（拒绝后不再反复弹窗，但仍会提示原因） */
let loginDeclined = false

/** 拒绝登录后的提示节流，避免连点时刷屏 */
let declineTipLock = false

/** 本次运行里是否已经为「已认证」换回过新 token（token 里的 vs 是登录快照，可能过期） */
let verifiedTokenReady = false

function exchange(code) {
  const payload = { code: code, platform: PLATFORM }
  // 只在显式开启本地演示时才带 devOpenid：
  // 后端 mock 分支会把它原样当 openid 用，常态发送会让不同微信账号合并成同一个用户
  if (DEV_LOGIN) payload.devOpenid = DEV_OPENID
  return http.post('/auth/login', payload).then(function (res) {
    mp.setStorageSync('token', res.token)
    mp.setStorageSync('user', res.user)
    const app = getApp()
    app.globalData.token = res.token
    app.globalData.user = res.user
    // 登录时后端按库里的最新状态签发 token（含 schoolId / verifyStatus），视为最新
    verifiedTokenReady = true
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

/**
 * 询问用户是否同意微信登录。
 *
 * `wx.login` 本身是无感的：一旦调用就会拿到 code 并在服务端建号。
 * 按产品要求，凡是「系统主动发起」的登录都必须先让用户确认，
 * 明确同意了才真正调用 wx.login。
 *
 * @returns {Promise<boolean>} 用户是否同意
 */
function confirmLogin() {
  return new Promise(function (resolve) {
    mp.showModal({
      title: '需要登录',
      content: '登录后即可点赞、评论和发布内容。我们会使用你的微信身份创建账号，'
        + '不会读取手机号或通讯录。登录即表示同意《用户协议》和《隐私政策》。',
      confirmText: '微信登录',
      cancelText: '先逛逛',
      confirmColor: '#FF8FA8',
      success: function (res) {
        resolve(!!res.confirm)
      },
      fail: function () {
        resolve(false)
      }
    })
  })
}

/**
 * 获取登录态。
 *
 * - 已有 token + 缓存用户 → 直接返回，不打扰
 * - 否则：
 *   - `force = true`：调用方已经获得用户明确意图（点了「一键登录」、或 404 自愈重建），
 *     不再二次确认，直接登录
 *   - 默认：先弹窗征求同意，用户拒绝则抛 401（`canceled: true`），绝不偷偷建号
 */
function ensureLogin(force) {
  const app = getApp()
  const cached = app.globalData.user || mp.getStorageSync('user')
  if (!force && http.getToken() && cached) {
    app.globalData.user = cached
    return Promise.resolve(cached)
  }
  if (pending) return pending

  let start
  if (force) {
    start = doLogin()
  } else if (loginDeclined) {
    // 本次运行里已经拒绝过一次：不再弹窗打断浏览，但要说明为什么没反应
    if (!declineTipLock) {
      declineTipLock = true
      toast('需要登录后才能操作，可到「我的」页登录')
      setTimeout(function () {
        declineTipLock = false
      }, 1500)
    }
    return Promise.reject({ code: 401, msg: '用户未登录', canceled: true })
  } else {
    start = confirmLogin().then(function (agreed) {
      if (!agreed) {
        loginDeclined = true
        throw { code: 401, msg: '用户取消登录', canceled: true }
      }
      return doLogin()
    })
  }

  pending = start.then(function (user) {
    pending = null
    loginDeclined = false
    return user
  }).catch(function (err) {
    pending = null
    throw err
  })
  return pending
}

/**
 * 发布类操作：必须已完成校园身份认证。
 *
 * 注意：认证状态在 JWT 里是**登录时的快照**（claim `vs`）。用户在别处/之后完成认证、
 * 或认证接口换回来的 token 没落到本地时，库里已经是 2，旧 token 仍会被后端判成未认证，
 * 报「请先完成校园身份认证后再发布内容」。所以这里先以 `/user/me`（库里的真实状态）为准，
 * 发现已认证就把 token 重签一次，再放行。
 */
function ensureVerified() {
  let cachedUser = null
  return ensureLogin().then(function (user) {
    cachedUser = user
    // 服务端为准；请求失败（网络异常）时退回缓存判断，不误拦发布
    return http.get('/user/me', null, { silent: true }).catch(function () {
      return user
    })
  }).then(function (profile) {
    if (profile && profile.verifyStatus === 2) {
      // 只写入服务端确实返回的字段：/user/me 失败时 profile 可能是缓存里的 UserBrief，
      // 它没有 schoolName，盲目写空串会把胶囊打回「选择学校」
      const patch = { verifyStatus: 2 }
      if (profile.id) patch.id = profile.id
      else if (cachedUser && cachedUser.id) patch.id = cachedUser.id
      if (profile.schoolId !== undefined && profile.schoolId !== null) patch.schoolId = profile.schoolId
      if (typeof profile.schoolName === 'string') patch.schoolName = profile.schoolName
      if (typeof profile.schoolCity === 'string') patch.schoolCity = profile.schoolCity
      updateUser(patch)
      // 一次会话内只重签一次，避免每次发布都多走一次 wx.login；
      // 失败不置位，下次发布还能再试
      if (!verifiedTokenReady) {
        return ensureLogin(true).then(function (fresh) {
          verifiedTokenReady = true
          return fresh
        })
      }
      return getApp().globalData.user
    }
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
 * 标记「本地 token 已是最新」（认证接口换回来的新 token 也走这里）。
 * 用于避免 `ensureVerified` 反复重签。
 */
function markTokenFresh() {
  verifiedTokenReady = true
}

/**
 * 清掉本地登录态并重新静默登录。
 *
 * 典型场景：数据库被重置（t_user 清空）后，本地还留着指向旧用户的 token，
 * 请求 /user/me 会返回「404 用户不存在」。此时必须重建会话，否则页面会一直用
 * 上一轮的缓存渲染出幽灵数据（旧昵称、旧学校、旧浏览范围）。
 *
 * @returns {Promise<Object>} 新的用户对象
 */
function relogin() {
  http.cleanLogin()
  return ensureLogin(true)
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
  relogin,
  markTokenFresh,
  platformInfo
}
