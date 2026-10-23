var http = require('./request')
var auth = require('./auth')

/**
 * 关注 / 取消关注共享逻辑（三处入口 + 个人主页共用）。
 * 收敛「保证登录 + 发请求」两件事；乐观更新 / 回滚 / 防重复点击由各页面自行处理。
 *
 * @param {String} userId 对方 id（字符串透传，禁止 Number()）
 * @param {Boolean} following 当前是否已关注（true → 取消关注）
 * @returns {Promise<{following:Boolean, fansCount:Number}>} 服务端最新状态与粉丝数
 */
function toggle(userId, following) {
  return auth.ensureLogin().then(function () {
    var url = '/user/follow/' + userId
    return following ? http.del(url) : http.post(url)
  })
}

module.exports = { toggle: toggle }
