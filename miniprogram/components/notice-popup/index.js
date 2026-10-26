/**
 * 公告弹窗。
 *
 * 只负责渲染与事件，不判断「该不该弹」——频次逻辑全在 utils/notice.js，
 * 这样游客/登录用户、本地/服务端两套判定不会散落在视图层。
 */
Component({
  properties: {
    visible: { type: Boolean, value: false },
    /** 已由 utils/notice.js#decorate 处理过的公告（含 typeText/levelText/permanent） */
    notice: { type: Object, value: null }
  },

  methods: {
    /** 吃掉遮罩上的滑动，避免弹层后面的首页跟着滚 */
    noop: function () {},

    onClose: function () {
      this.triggerEvent('close')
    },

    onConfirm: function () {
      this.triggerEvent('confirm')
    }
  }
})
