const { mp } = require('./util')

/**
 * 微信激励视频广告封装。
 *
 * 几个容易踩的点，都在这里处理掉了：
 * 1. **必须单例**：每次 create 都新建一个实例，重复调用会拿到多个广告实例，
 *    回调互相串台（A 的 onClose 收到 B 的结果）。按 adUnitId 缓存一个。
 * 2. **每次播放前重绑回调**：onClose 只能有一个有效监听，
 *    不 off 掉上一次的，第二次播放会同时触发两次回调。
 * 3. **show 失败要先 load**：广告还没拉取完时 show() 直接 reject，
 *    标准做法是 load() 完成后再 show()。
 * 4. **必须加超时兜底**：极端情况下（广告进程异常）onClose / onError 都不来，
 *    Promise 永远悬挂，页面会一直卡在「观看中」。
 *
 * 只有看完整段视频（res.isEnded === true）才 resolve(true)，
 * 中途退出一律按失败处理 —— 排序权重是稀缺资源，半途退出不能给。
 */

/** 单例缓存 */
let cached = null
let cachedUnitId = ''

function supported() {
  return typeof mp.createRewardedVideoAd === 'function'
}

function instance(unitId) {
  if (!supported() || !unitId) return null
  if (cached && cachedUnitId === unitId) return cached
  try {
    cached = mp.createRewardedVideoAd({ adUnitId: unitId, multiton: false })
    cachedUnitId = unitId
  } catch (e) {
    console.error('[ad] createRewardedVideoAd 失败', e)
    cached = null
  }
  return cached
}

function normalize(e) {
  if (!e) return { errMsg: 'AD_ERROR' }
  if (typeof e === 'string') return { errMsg: e }
  return e
}

/** 人类可读的失败原因，方便直接 toast */
function reason(e) {
  const raw = (e && (e.errMsg || e.errCode)) || ''
  const text = String(raw)
  if (text.indexOf('NOT_SUPPORTED') >= 0) return '当前版本不支持激励视频广告'
  if (text.indexOf('NOT_FINISHED') >= 0) return '视频还没看完，这次推广不计入'
  if (String(e && e.errCode) === '1004' || text.indexOf('no advertisement') >= 0) {
    return '广告暂时没有库存，请稍后再试'
  }
  if (text.indexOf('unitId') >= 0 || text.indexOf('adUnitId') >= 0) {
    return '广告位未配置，请联系管理员'
  }
  if (text.indexOf('timeout') >= 0) return '广告加载超时，请稍后再试'
  return '广告加载失败，请稍后再试'
}

/**
 * 播放激励视频。
 * @param {string} unitId 广告位 ID
 * @param {number} timeoutMs 超时兜底，默认 90 秒
 * @returns {Promise<boolean>} 只有完整看完才 resolve(true)
 */
function show(unitId, timeoutMs) {
  return new Promise(function (resolve, reject) {
    const ad = instance(unitId)
    if (!ad) {
      reject({ errMsg: 'AD_NOT_SUPPORTED' })
      return
    }
    let settled = false
    let timer = null

    const off = function () {
      try {
        if (ad.offClose) ad.offClose(onClose)
        if (ad.offError) ad.offError(onError)
      } catch (e) {
        /* 老版本没有 off* 方法，忽略 */
      }
    }

    function finish(ok, err) {
      if (settled) return
      settled = true
      if (timer) clearTimeout(timer)
      off()
      ok ? resolve(true) : reject(err || { errMsg: 'AD_ERROR' })
    }

    function onClose(res) {
      // isEnded 为 true 才是看完；提前点关闭不给奖励
      finish(!!(res && res.isEnded), { errMsg: 'AD_NOT_FINISHED' })
    }

    function onError(e) {
      console.error('[ad] onError', e)
      finish(false, normalize(e))
    }

    ad.onClose(onClose)
    if (ad.onError) ad.onError(onError)

    timer = setTimeout(function () {
      finish(false, { errMsg: 'AD_TIMEOUT' })
    }, timeoutMs || 90000)

    const tryShow = function () {
      return ad.show().catch(function () {
        // 广告未就绪：先 load 再 show。load 的失败会走 onError
        return ad.load().then(function () {
          return ad.show()
        })
      })
    }

    tryShow().catch(function (e) {
      // show 的 reject 不一定伴随 onError（例如重复调用），这里补一次收口，
      // 但给它一点时间：load 成功后 show 可能异步完成
      setTimeout(function () {
        finish(false, normalize(e))
      }, 300)
    })
  })
}

module.exports = {
  supported: supported,
  show: show,
  reason: reason
}
