const http = require('../../../utils/request')
const { mp, toast } = require('../../../utils/util')

Page({
  data: {
    statusBarHeight: 20,
    groupId: '',
    group: null,
    loading: true,
    saving: false,
    notifyMode: 0,
    notifyOptions: [
      { value: 0, title: '仅 @我 时提醒', desc: '默认。群里有人说话不打扰，被点名才提醒' },
      { value: 1, title: '全部消息提醒', desc: '人多时会比较吵，适合小群' },
      { value: 2, title: '完全静音', desc: '连 @我 也不提醒，只在进群时看到' }
    ]
  },

  onLoad: function (opt) {
    const info = typeof mp.getWindowInfo === 'function' ? mp.getWindowInfo() : mp.getSystemInfoSync()
    this.setData({
      statusBarHeight: info.statusBarHeight || 20,
      groupId: String((opt && opt.id) || '')
    })
    this.load()
  },

  load: function () {
    const self = this
    http.get('/groups/' + this.data.groupId, null, { silent: true }).then(function (d) {
      self.setData({
        group: d,
        notifyMode: d.notifyMode == null ? 0 : d.notifyMode,
        loading: false
      })
    }).catch(function () {
      self.setData({ loading: false })
      toast('加载失败，请返回重试')
    })
  },

  goBack: function () {
    mp.navigateBack()
  },

  onPickNotify: function (e) {
    const mode = Number(e.currentTarget.dataset.value)
    if (mode === this.data.notifyMode || this.data.saving) return
    const self = this
    const prev = this.data.notifyMode
    // 先改 UI 再请求（开关类操作要跟手），失败再回滚
    this.setData({ notifyMode: mode, saving: true })
    http.put('/groups/' + this.data.groupId + '/notify', { notifyMode: mode }, { silent: true })
      .then(function () {
        self.setData({ saving: false })
        toast('已保存')
      }).catch(function () {
        self.setData({ notifyMode: prev, saving: false })
        toast('保存失败，请重试')
      })
  },

  goMembers: function () {
    mp.navigateTo({ url: '/subpackages/msg/group/members?id=' + this.data.groupId })
  },

  /** 退群：二次确认，且说明「不会再被自动拉回」 */
  onQuit: function () {
    const self = this
    mp.showModal({
      title: '退出群聊',
      content: '退出后不会再自动加入，需要时可以在这个页面重新加入。确定退出吗？',
      confirmText: '退出',
      confirmColor: '#ef4444',
      success: function (res) {
        if (!res.confirm) return
        http.post('/groups/' + self.data.groupId + '/quit', null, { silent: true }).then(function () {
          toast('已退出群聊')
          setTimeout(function () {
            mp.navigateBack({ delta: 2 })
          }, 800)
        }).catch(function () {
          toast('退群失败，请重试')
        })
      }
    })
  },

  /** 举报入口：跳到全站统一的举报页 */
  goReport: function () {
    mp.navigateTo({ url: '/pages/report/report?type=GROUP&id=' + this.data.groupId })
  }
})
