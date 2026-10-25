const { mp } = require('../../utils/util')

Component({
  options: { multipleSlots: true, addGlobalClass: true },
  properties: {
    title: { type: String, value: '' },
    showBack: { type: Boolean, value: false },
    backText: { type: String, value: '' },
    bg: { type: String, value: '#ffffff' },
    color: { type: String, value: '' },
    fixed: { type: Boolean, value: true },
    transparent: { type: Boolean, value: false }
  },
  data: { statusBarHeight: 20, navBarHeight: 44, totalHeight: 64 },
  lifetimes: {
    attached: function () {
      let status = 20
      let navH = 44
      try {
        const info = mp.getSystemInfoSync()
        status = info.statusBarHeight || 20
        const menu = mp.getMenuButtonBoundingClientRect && mp.getMenuButtonBoundingClientRect()
        if (menu && menu.height) {
          navH = (menu.top - status) * 2 + menu.height
        }
      } catch (e) {}
      this.setData({ statusBarHeight: status, navBarHeight: navH, totalHeight: status + navH })
    }
  },
  methods: {
    onBack: function () {
      const pages = mp.getCurrentPages && mp.getCurrentPages()
      if (pages && pages.length > 1) {
        mp.navigateBack()
      } else {
        this.triggerEvent('back')
      }
    }
  }
})
