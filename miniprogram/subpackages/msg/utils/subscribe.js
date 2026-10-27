/**
 * 订阅消息（微信「服务通知」）封装。
 *
 * 现状：只做「授权」，不做发送。发送必须在服务端（要 access_token 和 AppSecret，
 * 前端永远拿不到）。后期绑定服务号 / 配好模板后，服务端按 t_message_setting 的
 * 开关决定推哪几类，本文件不用改。
 *
 * ⚠️ 两条硬约束，踩了会白折腾：
 *
 * 1. **必须在用户点击的同步调用栈里调用**。`wx.requestSubscribeMessage` 一旦
 *    夹在任何 await / 网络请求之后，就会报
 *    `can only be invoked by user TAP gesture`。
 *    所以模板 id 一定要提前拉到（页面 onLoad 时随设置一起拿），点击时直接用缓存，
 *    绝不能在点击回调里再发请求拿 id。
 *
 * 2. **模板 id 不能让前端写死**。它来自小程序后台「订阅消息 → 我的模板」，
 *    由后端配置下发（campus.wx.subscribe-templates）。没配就返回空数组，
 *    此时前端应「只展示入口、不发起授权」—— 拿空数组调接口只会失败。
 */
const { mp } = require('../../../utils/util')

/** 一次最多能请求的模板数（官方上限以文档为准，这里按 3 兜底，超了接口会直接报错） */
const MAX_TMPL = 3

/** 当前环境是否支持订阅消息（QQ 小程序没有这个接口） */
function isSupported() {
  return typeof mp.requestSubscribeMessage === 'function'
}

/**
 * 请求订阅授权。**必须在 tap 回调里直接调用，前面不能有 await**。
 *
 * @param {string[]} tmplIds 后端下发的模板 id
 * @returns {Promise<{status:number, accepted:string[], reason:string}>}
 *          status: 1 至少一个允许 / 2 全部拒绝 / 0 无法请求（未配置或不支持）
 *          reason: 'no-template' | 'unsupported' | 'user-reject' | 'accept' | 'fail'
 */
function request(tmplIds) {
  return new Promise(function (resolve) {
    const ids = (tmplIds || []).filter(function (id) {
      return typeof id === 'string' && id
    }).slice(0, MAX_TMPL)

    if (!ids.length) {
      resolve({ status: 0, accepted: [], reason: 'no-template' })
      return
    }
    if (!isSupported()) {
      resolve({ status: 0, accepted: [], reason: 'unsupported' })
      return
    }

    mp.requestSubscribeMessage({
      tmplIds: ids,
      success: function (res) {
        // res 形如 { 'tmplId': 'accept' | 'reject' | 'ban', errMsg: '...' }
        const accepted = ids.filter(function (id) { return res[id] === 'accept' })
        resolve({
          status: accepted.length ? 1 : 2,
          accepted: accepted,
          reason: accepted.length ? 'accept' : 'user-reject'
        })
      },
      fail: function (err) {
        // 常见原因：模板 id 无效、额度用尽、不在 tap 手势里调用
        resolve({ status: 0, accepted: [], reason: 'fail', err: err })
      }
    })
  })
}

module.exports = {
  isSupported: isSupported,
  request: request,
  MAX_TMPL: MAX_TMPL
}
