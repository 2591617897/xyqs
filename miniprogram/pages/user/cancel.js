const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const ui = require('../../utils/ui')

Page({
  data: {
    /** 勾选「我已了解」，不勾不给点：注销是不可撤销的操作，必须有明确动作 */
    agreed: false,
    submitting: false,
    /** 待注销账号的昵称 / 学校，让用户确认注销的是哪个号（多账号场景） */
    nickname: '',
    schoolName: ''
  },

  onLoad: function () {
    mp.setNavigationBarTitle({ title: '注销账号' })
    const self = this
    // 注销页必须登录：拿不到身份就退回去，别让用户对着一个点不动的按钮
    auth.ensureLogin().then(function () {
      return http.get('/user/me', null, { silent: true })
    }).then(function (me) {
      self.setData({
        nickname: me.nickname || '',
        schoolName: me.schoolName || ''
      })
    }).catch(function () {
      // 用户拒绝登录 / 接口异常：保持页面可读，点提交时会再走一次登录
    })
  },

  toggleAgree: function () {
    this.setData({ agreed: !this.data.agreed })
  },

  back: function () {
    mp.navigateBack()
  },

  submit: function () {
    const self = this
    if (this.data.submitting) return
    if (!this.data.agreed) {
      toast('请先阅读并勾选注销须知')
      return
    }
    ui.confirm({
      title: '确认注销账号？',
      content: '注销后立即退出登录，昵称显示为「已注销用户」，校园认证与手机号会被清除。1 年内用这个微信重新登录还能恢复。',
      confirmText: '确认注销',
      cancelText: '再想想'
    }).then(function (ok) {
      if (!ok) return
      self.setData({ submitting: true })
      return http.post('/user/cancel').then(function () {
        self.done()
      }).catch(function () {
        // 失败原因（账号已注销 / 网络）已由 request 层 toast，这里只解锁按钮
        self.setData({ submitting: false })
      })
    })
  },

  /** 注销成功：清本地登录态 → 明确告知后果 → 回首页（游客态） */
  done: function () {
    // 先清登录态：旧 token 已不可用，留着只会让页面继续渲染出幽灵资料
    http.cleanLogin()
    // 本次运行不再主动弹「需要登录」，否则刚注销就被登录框追着问
    auth.suspendPrompt()
    mp.showModal({
      title: '账号已注销',
      content: '资料会在 1 年后彻底删除。这期间用同一个微信重新登录，可以恢复账号（校园认证需重新完成）。',
      showCancel: false,
      confirmText: '知道了',
      success: function () {
        mp.reLaunch({ url: '/pages/index/index' })
      },
      fail: function () {
        mp.reLaunch({ url: '/pages/index/index' })
      }
    })
  }
})
