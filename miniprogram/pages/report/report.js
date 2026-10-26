const { mp, toast } = require('../../utils/util')
const http = require('../../utils/request')
const auth = require('../../utils/auth')

const TARGET_LABELS = {
  POST: '帖子',
  WALL: '表白墙',
  GOODS: '闲置商品',
  COMMENT: '评论',
  USER: '用户',
  MESSAGE: '私信',
  FEEDBACK: '意见反馈'
}
const REASONS = ['色情低俗', '暴力血腥', '诈骗信息', '广告骚扰', '人身攻击', '虚假信息', '侵权举报', '其他']

Page({
  data: {
    targetType: '',
    targetId: '',
    targetLabel: '',
    isFeedback: false,
    reasons: REASONS,
    reasonIndex: -1,
    reasonDesc: '',
    submitting: false
  },

  onLoad(query) {
    const targetType = ((query && query.type) || 'FEEDBACK').toUpperCase()
    const targetId = query && query.id ? query.id : ''
    const isFeedback = targetType === 'FEEDBACK'
    this.setData({
      targetType: targetType,
      targetId: targetId,
      targetLabel: TARGET_LABELS[targetType] || '内容',
      isFeedback: isFeedback
    })
    mp.setNavigationBarTitle({ title: isFeedback ? '意见反馈' : '举报' })
  },

  chooseReason(e) {
    this.setData({ reasonIndex: e.currentTarget.dataset.index })
  },

  onDescInput(e) {
    this.setData({ reasonDesc: e.detail.value })
  },

  submit() {
    if (this.data.submitting) return
    const d = this.data
    if (!d.isFeedback && !d.targetId) {
      toast('缺少举报对象')
      return
    }
    if (d.reasonIndex < 0) {
      toast('请选择举报原因')
      return
    }
    const data = {
      targetType: d.targetType || 'FEEDBACK',
      // ⚠️ 雪花 id 是 19 位，超过 Number.MAX_SAFE_INTEGER，
      // 一旦 Number() 转换就会丢精度（2114021598989209601 → 2114021598989209600），
      // 工单会挂到错误的内容上。全站 id 一律以字符串传递，后端 Jackson/Long 会自动承接。
      targetId: d.targetId ? String(d.targetId) : null,
      reasonType: d.reasons[d.reasonIndex],
      reasonDesc: d.reasonDesc
    }
    this.setData({ submitting: true })
    // 举报是写操作，后端要求登录；游客直接提交只会拿到 401，
    // 所以先走统一的登录守卫（用户点「提交」＝明确意图，弹窗确认后登录再发请求）。
    // ⚠️ utils/request 导出的是 { request, get, post, put, del }，
    // 不是函数本身 —— 这里必须用 http.post(...)，写成 http({...}) 会抛 http is not a function。
    auth.ensureLogin()
      .then(function () {
        return http.post('/user/report', data, { loading: '提交中' })
      })
      .then(function () {
        toast('提交成功，我们会尽快核实处理')
        setTimeout(function () { mp.navigateBack() }, 800)
      })
      .catch(function () {})
      .then(() => this.setData({ submitting: false }))
  }
})
