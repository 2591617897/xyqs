const promote = require('../../utils/promote')
const { mp, toast } = require('../../utils/util')

/**
 * 推广弹层：看一段激励视频，换自己内容的排序权重。
 *
 * 页面只需要传 targetType / targetId 并控制 visible，
 * 配额拉取、广告播放、结算、失败作废都封在组件里。
 * 结算成功后 triggerEvent('done', { score })，页面自行刷新列表。
 */
Component({
  properties: {
    visible: { type: Boolean, value: false },
    /** GOODS / POST / WALL */
    targetType: { type: String, value: '' },
    /** 一律字符串：雪花 ID 是 19 位，走 Number 会丢精度 */
    targetId: { type: String, value: '' },
    title: { type: String, value: '推广这条内容' },
    /** 内容类型展示名，如「闲置」「帖子」「表白」 */
    targetName: { type: String, value: '内容' }
  },

  data: {
    safeArea: 0,
    loading: false,
    busy: false,
    countdown: 0,
    quota: null,
    error: '',
    /** 推广分进度（相对「单条每日上限 × 单次基础分」的满格） */
    percent: 0,
    remainText: ''
  },

  lifetimes: {
    attached: function () {
      try {
        const sa = mp.getSystemInfoSync().safeAreaInsets || {}
        this.setData({ safeArea: sa.bottom || 0 })
      } catch (e) {
        /* 取不到安全区就用 0 */
      }
    }
  },

  observers: {
    'visible, targetType, targetId': function (visible, type, id) {
      if (visible && type && id) this.refresh()
    }
  },

  methods: {
    refresh: function () {
      const self = this
      this.setData({ loading: true, error: '' })
      return promote.quota(this.data.targetType, this.data.targetId)
        .then(function (q) {
          self.setData({ quota: q, loading: false })
          self.decorate(q)
        })
        .catch(function (e) {
          self.setData({
            loading: false,
            error: (e && e.msg) || '推广状态加载失败'
          })
        })
    },

    /** 把后端配额算成可直接渲染的展示字段 */
    decorate: function (q) {
      if (!q) return
      const full = Math.max(1, (q.dailyPerTarget || 5) * (q.baseScore || 100))
      const percent = Math.min(100, Math.round((q.score || 0) / full * 100))
      this.setData({ percent: percent, remainText: remainDays(q.expireAt) })
    },

    start: function () {
      const self = this
      if (this.data.busy) return
      const q = this.data.quota
      if (!q) return
      if (q.remainTarget <= 0) {
        toast('这条内容今天的推广次数用完了')
        return
      }
      if (q.remainUser <= 0) {
        toast('你今天的推广次数用完了，明天再来')
        return
      }
      // 联调模式之外必须有广告位：没配广告位就别让用户白看一场
      if (!q.mockAd && !q.hasAdUnit) {
        toast('推广功能未开启（缺少广告位配置）')
        return
      }

      this.setData({ busy: true, countdown: 0 })
      promote
        .run(this.data.targetType, this.data.targetId, {
          onPhase: function (phase, extra) {
            if (phase === 'countdown') {
              self.setData({ countdown: extra.seconds })
            }
          }
        })
        .then(function (res) {
          self.setData({ busy: false, countdown: 0 })
          toast('推广成功，+' + (res.delta || 0) + ' 分')
          self.triggerEvent('done', { score: res.score || 0 })
          return self.refresh()
        })
        .catch(function (e) {
          self.setData({ busy: false, countdown: 0 })
          toast(promote.failText(e))
        })
    },

    close: function () {
      if (this.data.busy) return
      this.triggerEvent('close')
    },

    /** 阻止冒泡：点弹层内部不能关掉弹层 */
    noop: function () {}
  }
})

/** 有效期剩余天数文案 */
function remainDays(expireAt) {
  if (!expireAt) return ''
  const t = new Date(String(expireAt).replace(/-/g, '/').replace('T', ' ').replace(/\.\d+$/, '')).getTime()
  if (isNaN(t)) return ''
  const hours = (t - Date.now()) / 3600000
  if (hours <= 0) return '已过期'
  if (hours < 24) return '剩余 ' + Math.max(1, Math.round(hours)) + ' 小时'
  return '剩余 ' + Math.floor(hours / 24) + ' 天'
}
