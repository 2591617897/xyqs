// pages/legal/privacy.js
const { CONTACT_EMAIL } = require('../../utils/config')

Page({
  data: {
    contactEmail: CONTACT_EMAIL
  },

  /** 长按邮箱可复制，方便用户自行发信（审核也认可「有效联系方式」） */
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
