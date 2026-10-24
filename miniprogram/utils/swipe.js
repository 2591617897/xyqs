/**
 * 左右滑动识别（频道 / 分类切换用）
 *
 * 为什么不用 swiper：首页和表白墙都是「页面级滚动 + 下拉刷新 + 触底分页」，
 * 换成 swiper 得把内容塞进定高的 scroll-view 里，下拉刷新、onReachBottom 全要重做，
 * 改动面太大、回归成本高。这里只做手势识别，命中后走和点 tab 完全相同的切换逻辑。
 *
 * 有效横滑必须同时满足：
 *   1. 横向位移 ≥ 60px
 *   2. 横向位移 ≥ 纵向位移 × 1.6（纵向为主说明用户在滚页面，让位给滚动）
 *   3. 手势时长 ≤ 600ms（慢慢拖不触发，避免误切）
 * move 阶段一旦判定纵向优先就直接放弃本次手势，不再和页面滚动抢。
 *
 * 用法：
 *   const swipe = require('../../utils/swipe')
 *   onLoad() { this._swipe = swipe.create({ next: () => {}, prev: () => {} }) }
 *   wxml: bindtouchstart="onSwipeStart" bindtouchmove="onSwipeMove" bindtouchend="onSwipeEnd"
 */
var MIN_DX = 60      // 最小横向位移（px）
var RATIO = 1.6      // 横向 / 纵向 位移比
var MAX_MS = 600     // 手势最长耗时

function create(opts) {
  var options = opts || {}
  var startX = 0
  var startY = 0
  var startAt = 0
  var active = false

  function point(list) {
    return (list && list[0]) || null
  }

  return {
    start: function (e) {
      var t = point(e.touches) || point(e.changedTouches)
      if (!t) {
        active = false
        return
      }
      startX = t.clientX
      startY = t.clientY
      startAt = Date.now()
      active = true
    },

    move: function (e) {
      if (!active) return
      var t = point(e.touches)
      if (!t) return
      var dx = Math.abs(t.clientX - startX)
      var dy = Math.abs(t.clientY - startY)
      // 纵向已经明显占优：立刻放弃，避免用户想滚页面却切了分类
      if (dy > 24 && dy > dx) active = false
    },

    end: function (e) {
      if (!active) return
      active = false
      var t = point(e.changedTouches) || point(e.touches)
      if (!t) return
      var dx = t.clientX - startX
      var dy = t.clientY - startY
      var ms = Date.now() - startAt
      if (Math.abs(dx) < MIN_DX) return
      if (Math.abs(dx) < Math.abs(dy) * RATIO) return
      if (ms > MAX_MS) return
      // 手指左滑（dx < 0）看下一个，右滑看上一个
      if (dx < 0) {
        if (options.next) options.next()
      } else if (options.prev) {
        options.prev()
      }
    }
  }
}

module.exports = { create: create }
