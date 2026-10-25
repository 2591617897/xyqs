const ui = require('../../utils/ui')

Component({
  data: { visible: false, text: '', icon: '' },
  lifetimes: {
    attached: function () {
      this._timer = null
      ui.setToastAdapter(this.show.bind(this))
    },
    detached: function () {
      if (this._timer) clearTimeout(this._timer)
    }
  },
  methods: {
    show: function (opts) {
      const o = opts || {}
      this.setData({ visible: true, text: o.title || '', icon: o.icon || '' })
      if (this._timer) clearTimeout(this._timer)
      const self = this
      this._timer = setTimeout(function () { self.setData({ visible: false }) }, o.duration || 1500)
    }
  }
})
