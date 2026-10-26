// pages/legal/rule.js
const { CONTACT_EMAIL } = require('../../utils/config')

Page({
  data: {
    contactEmail: CONTACT_EMAIL
  },

  copyEmail() {
    const { mp } = require('../../utils/util')
    mp.setClipboardData({
      data: CONTACT_EMAIL,
      success() {
        mp.showToast({ title: '邮箱已复制', icon: 'none' })
      }
    })
  }
})
