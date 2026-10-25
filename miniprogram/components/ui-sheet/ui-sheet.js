const { mp } = require('../../utils/util')

Component({
  properties: {
    visible: { type: Boolean, value: false },
    title: { type: String, value: '' }
  },
  data: { safeArea: 0 },
  lifetimes: {
    attached: function () {
      const sa = mp.getSystemInfoSync().safeAreaInsets || { bottom: 0 }
      this.setData({ safeArea: sa.bottom || 0 })
    }
  },
  methods: {
    close: function () { this.triggerEvent('close') }
  }
})
