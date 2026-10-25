const http = require('../../utils/request')

/**
 * 后台公告详情。
 *
 * 数据来自 campus-post 的 GET /notices/{id}（读 t_admin_notice），
 * 后台已经按 target_type 做过隔离，小程序拿不到「仅管理员可见」的内部通知。
 */
Page({
  data: {
    notice: null,
    loading: true,
    errorMsg: ''
  },

  onLoad: function (options) {
    if (!options || !options.id) {
      this.setData({ loading: false, errorMsg: '公告不存在' })
      return
    }
    this.load(options.id)
  },

  load: function (id) {
    const self = this
    this.setData({ loading: true })
    http.get('/notices/' + id, null, { silent: true }).then(function (res) {
      if (!res) {
        self.setData({ loading: false, errorMsg: '公告不存在或已下线' })
        return
      }
      self.setData({ notice: decorate(res), loading: false, errorMsg: '' })
    }).catch(function () {
      self.setData({ loading: false, errorMsg: '加载失败，请稍后重试' })
    })
  }
})

/** 类型与日期在渲染前算好，避免 wxml 里写判断逻辑 */
function decorate(n) {
  const TYPE = { 1: '系统公告', 2: '运营活动', 3: '违规警示' }
  return {
    id: n.id,
    title: n.title || '',
    content: n.content || '',
    typeText: TYPE[n.type] || '公告',
    level: n.level || 1,
    publishText: String(n.publishAt || '').replace('T', ' ').slice(0, 16)
  }
}
