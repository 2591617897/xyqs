const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const upload = require('../../utils/upload')

const GENDER_OPTIONS = ['保密', '男', '女']

Page({
  data: {
    logged: false,
    user: null,
    // 可编辑表单
    nickname: '',
    genderIndex: 0,
    genderOptions: GENDER_OPTIONS,
    phone: '',
    bio: '',
    bioCount: 0,
    saving: false,
    mockMode: http.USE_MOCK
  },

  onShow: function () {
    if (!http.getToken()) {
      this.setData({ logged: false, user: null })
      return
    }
    this.loadMe()
  },

  loadMe: function () {
    const self = this
    http.get('/user/me', null, { silent: true }).then(function (me) {
      self.applyMe(me)
    }).catch(function (err) {
      if (err && err.code === 401) {
        self.setData({ logged: false, user: null })
      }
    })
  },

  applyMe: function (me) {
    this.setData({
      logged: true,
      user: me,
      nickname: me.nickname || '',
      genderIndex: me.gender >= 0 && me.gender <= 2 ? me.gender : 0,
      phone: me.phone || '',
      bio: me.bio || '',
      bioCount: (me.bio || '').length
    })
    this.syncCache(me)
  },

  syncCache: function (me) {
    auth.updateUser({
      id: me.id,
      nickname: me.nickname,
      avatar: me.avatar,
      schoolId: me.schoolId,
      schoolName: me.schoolName || '',
      verifyStatus: me.verifyStatus
    })
  },

  login: function () {
    const self = this
    toast('正在登录…')
    auth.ensureLogin(true).then(function () {
      self.loadMe()
    }).catch(function (err) {
      console.error('[login] 失败', err)
      toast(auth.loginErrorText(err))
    })
  },

  /**
   * 微信取头像：open-type="chooseAvatar" 返回临时路径，
   * 必须先上传换成可访问 URL 再入库
   */
  onChooseAvatar: function (e) {
    const self = this
    if (!this.data.logged) {
      this.login()
      return
    }
    const tmpPath = e && e.detail && e.detail.avatarUrl
    if (!tmpPath) {
      toast('未获取到头像，请重试')
      return
    }
    mp.showLoading({ title: '上传中', mask: true })
    upload.uploadImage(tmpPath).then(function (url) {
      mp.hideLoading()
      return http.put('/user/me', { avatar: url }, { silent: true })
    }).then(function (me) {
      self.applyMe(me)
      toast('头像已更新')
    }).catch(function () {
      mp.hideLoading()
      toast('头像上传失败')
    })
  },

  /** 兜底：从相册选一张作为头像 */
  chooseFromAlbum: function () {
    const self = this
    if (!this.data.logged) {
      this.login()
      return
    }
    mp.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      success: function (res) {
        const path = res.tempFilePaths && res.tempFilePaths[0]
        if (!path) return
        mp.showLoading({ title: '上传中', mask: true })
        upload.uploadImage(path).then(function (url) {
          mp.hideLoading()
          return http.put('/user/me', { avatar: url }, { silent: true })
        }).then(function (me) {
          self.applyMe(me)
          toast('头像已更新')
        }).catch(function () {
          mp.hideLoading()
          toast('头像上传失败')
        })
      }
    })
  },

  onNicknameInput: function (e) {
    this.setData({ nickname: e.detail.value })
  },

  onGenderChange: function (e) {
    this.setData({ genderIndex: Number(e.detail.value) })
  },

  onPhoneInput: function (e) {
    this.setData({ phone: e.detail.value })
  },

  onBioInput: function (e) {
    const v = e.detail.value || ''
    this.setData({ bio: v, bioCount: v.length })
  },

  /** 保存全部可编辑字段 */
  save: function () {
    const self = this
    if (!this.data.logged || this.data.saving) return
    const nickname = (this.data.nickname || '').trim()
    if (!nickname) {
      toast('昵称不能为空')
      return
    }
    const phone = (this.data.phone || '').trim()
    if (phone && !/^1\d{10}$/.test(phone)) {
      toast('手机号格式不正确')
      return
    }
    this.setData({ saving: true })
    http.put('/user/me', {
      nickname: nickname,
      gender: this.data.genderIndex,
      phone: phone,
      bio: (this.data.bio || '').trim()
    }, { loading: '保存中' }).then(function (me) {
      self.applyMe(me)
      self.setData({ saving: false })
      toast('已保存')
      setTimeout(function () {
        mp.navigateBack()
      }, 600)
    }).catch(function () {
      self.setData({ saving: false })
    })
  },

  goVerify: function () {
    if (!this.data.logged) {
      this.login()
      return
    }
    mp.navigateTo({ url: '/pages/user/verify' })
  }
})
