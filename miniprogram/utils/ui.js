/**
 * 统一 UI 反馈层：收口 Toast / Loading / Modal。
 *
 * 现状：全仓约 20 处直接调 mp.showToast、13 处 mp.showLoading，风格不一。
 * 这里提供统一入口；后续批次把那些直调迁到 ui.showToast / ui.showLoading 即可，
 * 不改行为也不影响现有页面。ui-toast 组件可经 setToastAdapter 接管渲染。
 */
const { mp } = require('./util')

let toastAdapter = null
let loadingCount = 0

/** 自定义 Toast 组件可注册适配器：setToastAdapter(fn)，fn({title, icon, duration}) */
function setToastAdapter(fn) {
  toastAdapter = fn
}

function showToast(opts) {
  if (toastAdapter) {
    toastAdapter(typeof opts === 'string' ? { title: opts } : opts)
    return
  }
  const title = typeof opts === 'string' ? opts : (opts && opts.title) || ''
  mp.showToast({
    title: title,
    icon: (opts && opts.icon) || 'none',
    duration: (opts && opts.duration) || 1500
  })
}

function showLoading(title) {
  loadingCount++
  mp.showLoading({ title: title || '加载中', mask: true })
}

function hideLoading() {
  loadingCount = Math.max(0, loadingCount - 1)
  if (loadingCount === 0) mp.hideLoading()
}

/** 二次确认，返回 Promise<boolean> */
function confirm(opts) {
  return new Promise(function (resolve) {
    mp.showModal({
      title: (opts && opts.title) || '提示',
      content: (opts && opts.content) || '',
      confirmText: (opts && opts.confirmText) || '确定',
      cancelText: (opts && opts.cancelText) || '取消',
      success: function (res) { resolve(!!res.confirm) }
    })
  })
}

module.exports = { showToast, setToastAdapter, showLoading, hideLoading, confirm }
