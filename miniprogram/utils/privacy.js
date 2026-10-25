/**
 * 隐私授权（微信「用户隐私保护指引」）
 *
 * 背景：微信自 2023-09-15 起对调用隐私接口（相册、定位、头像等）的小程序强制校验
 * 用户隐私保护指引。app.json 已声明 `__usePrivacyCheck__: true`，且**有意不注册**
 * `wx.onNeedPrivacyAuthorization` —— 这样基础库（≥2.32.3）会弹出微信官方隐私弹窗，
 * 用户同意后自动放行后续隐私接口。
 *
 * 为什么不自己实现 onNeedPrivacyAuthorization：
 * 自定义弹窗必须正确回调 `resolve({ buttonId, event: 'agree' })`，且 buttonId 要对应
 * 页面里真实存在的 `<button open-type="agreePrivacyAuthorization">`。时机或 id 错了
 * 会造成授权死锁（弹窗不弹、隐私接口全被拦截），风险远高于用官方弹窗。
 *
 * 本模块只提供两个「查状态 / 看协议」的轻能力，供「我的 → 隐私设置」使用。
 */
const { mp } = require('./util')

const FALLBACK_NAME = '《校园聚隐私政策》'

function supported(name) {
  return typeof mp[name] === 'function'
}

/**
 * 查询隐私授权状态。
 * @returns {Promise<{supported: boolean, needAuthorization: boolean, contractName: string}>}
 */
function setting() {
  return new Promise(function (resolve) {
    const fallback = { supported: false, needAuthorization: false, contractName: FALLBACK_NAME }
    if (!supported('getPrivacySetting')) {
      resolve(fallback)
      return
    }
    try {
      mp.getPrivacySetting({
        success: function (res) {
          resolve({
            supported: true,
            needAuthorization: !!(res && res.needAuthorization),
            contractName: (res && res.privacyContractName) || FALLBACK_NAME
          })
        },
        fail: function () {
          resolve(fallback)
        }
      })
    } catch (e) {
      resolve(fallback)
    }
  })
}

/**
 * 打开微信托管的隐私协议页。
 * @returns {Promise<boolean>} 是否成功打开
 */
function openContract() {
  return new Promise(function (resolve) {
    if (!supported('openPrivacyContract')) {
      resolve(false)
      return
    }
    try {
      mp.openPrivacyContract({
        success: function () { resolve(true) },
        fail: function () { resolve(false) }
      })
    } catch (e) {
      resolve(false)
    }
  })
}

module.exports = {
  setting: setting,
  openContract: openContract
}
