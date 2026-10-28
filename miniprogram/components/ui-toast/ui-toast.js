const ui = require('../../utils/ui')

Component({
  data: { visible: false, leaving: false, text: '', icon: '' },
  lifetimes: {
    attached: function () {
      this._timer = null
      this._outTimer = null
      ui.setToastAdapter(this.show.bind(this))
    },
    detached: function () {
      if (this._timer) clearTimeout(this._timer)
      if (this._outTimer) clearTimeout(this._outTimer)
    }
  },
  methods: {
    show: function (opts) {
      const o = opts || {}
      if (this._timer) { clearTimeout(this._timer); this._timer = null }
      if (this._outTimer) { clearTimeout(this._outTimer); this._outTimer = null }
      // 先复位离开态，确保每次都重放入场动画
      this.setData({ visible: true, leaving: false, text: o.title || '', icon: o.icon || '' })
      const self = this
      const dur = o.duration || 1500
      this._timer = setTimeout(function () {
        // 先播放退场动画，再卸载节点
        self.setData({ leaving: true })
        self._outTimer = setTimeout(function () {
          self.setData({ visible: false, leaving: false })
        }, 220)
      }, dur)
    }
  }
})
