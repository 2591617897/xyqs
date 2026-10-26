const http = require('./request')
const ad = require('./rewarded-ad')

/**
 * 内容推广：看一段激励视频，换自己内容的排序权重。
 *
 * 流程固定为「签发凭证 → 播广告 → 结算」三步，中间任何一步失败都要 cancel，
 * 否则那张凭证会一直占着当日配额（用户手滑一次就少一次机会）。
 */

/**
 * 当前推广状态 + 今日剩余次数
 */
function quota(targetType, targetId) {
  return http.get('/promote/quota', { targetType: targetType, targetId: targetId }, { silent: true })
}

function issue(targetType, targetId) {
  return http.post('/promote/issue', { targetType: targetType, targetId: targetId }, { silent: true })
}

function complete(token) {
  return http.post('/promote/complete', { token: token }, { silent: true })
}

/** 放弃本次推广，释放配额。失败无所谓，静默吞掉 */
function cancel(token) {
  if (!token) return Promise.resolve()
  return http.post('/promote/cancel', { token: token }, { silent: true }).catch(function () {})
}

/**
 * 联调模式下的「模拟观看」：服务端允许跳过真实广告，
 * 但最短观看时长的校验还在，所以这里同样要等满那么多秒。
 * 每整秒回调一次，页面用来显示倒计时。
 */
function waitSeconds(seconds, onTick) {
  return new Promise(function (resolve) {
    let left = seconds
    if (onTick) onTick(left)
    const timer = setInterval(function () {
      left -= 1
      if (onTick) onTick(left)
      if (left <= 0) {
        clearInterval(timer)
        resolve(true)
      }
    }, 1000)
  })
}

/**
 * 走完一次推广。
 *
 * @param {string} targetType GOODS / POST / WALL
 * @param {string|number} targetId
 * @param {object} opts { onPhase } onPhase('issuing'|'watching'|'settling'|'countdown', 附加数据)
 * @returns {Promise<{score:number, delta:number}>}
 */
function run(targetType, targetId, opts) {
  const cb = (opts && opts.onPhase) || function () {}
  let token = ''

  return issue(targetType, targetId)
    .then(function (res) {
      token = res.token
      cb('issuing', res)
      if (res.mockAd) {
        // 多等 1 秒再上报：倒计时本身有毫秒级误差，加上网络往返，
        // 卡着 minWatchSeconds 上报会被服务端判成「没看完」（边界踩坑）。
        // 真实广告时长不固定，不存在这个问题。
        const need = res.minWatchSeconds || 15
        cb('countdown', { seconds: need })
        return waitSeconds(need + 1, function (left) {
          cb('countdown', { seconds: Math.max(0, left - 1) })
        })
      }
      cb('watching', res)
      return ad.show(res.adUnitId)
    })
    .then(function () {
      cb('settling')
      return complete(token)
    })
    .then(function (res) {
      token = ''
      return res
    })
    .catch(function (e) {
      // 任何一步失败都要作废凭证，否则它会一直占着当日配额
      if (token) cancel(token)
      throw e
    })
}

/** 把失败翻译成能直接 toast 的文案 */
function failText(e) {
  const msg = (e && e.msg) || ''
  if (e && e.network) return '网络异常，请稍后重试'
  if (msg) return msg
  return ad.reason(e)
}

module.exports = {
  quota: quota,
  run: run,
  cancel: cancel,
  failText: failText
}
