const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')

Page({
  data: {
    schools: [],
    filtered: [],
    keyword: '',
    schoolId: null,
    schoolName: '',
    studentNo: '',
    realName: '',
    verifyImg: '',
    submitting: false,
    showPicker: false,
    done: false,
    doneStatus: 0,
    alreadyVerified: false
  },

  onLoad: function () {
    mp.setNavigationBarTitle({ title: '校园身份认证' })
    const self = this
    auth.ensureLogin().then(function (user) {
      if (user && user.verifyStatus === 2) {
        self.setData({ alreadyVerified: true, done: true, doneStatus: 2 })
      }
      self.loadSchools()
    }).catch(function () {
      self.loadSchools()
    })
  },

  loadSchools: function () {
    const self = this
    http.get('/schools', null, { silent: true }).then(function (list) {
      self.setData({ schools: list || [], filtered: list || [] })
    }).catch(function () {
      toast('学校列表加载失败')
    })
  },

  openPicker: function () {
    this.setData({ showPicker: true, keyword: '', filtered: this.data.schools })
  },

  closePicker: function () {
    this.setData({ showPicker: false })
  },

  onKeyword: function (e) {
    const kw = e.detail.value.trim()
    const filtered = kw
      ? this.data.schools.filter(function (s) {
          return s.name.indexOf(kw) >= 0 || (s.city && s.city.indexOf(kw) >= 0)
        })
      : this.data.schools
    this.setData({ keyword: kw, filtered: filtered })
  },

  pickSchool: function (e) {
    const id = Number(e.currentTarget.dataset.id)
    const school = this.data.schools.filter(function (s) {
      return s.id === id
    })[0]
    if (!school) return
    this.setData({
      schoolId: school.id,
      schoolName: school.name,
      showPicker: false
    })
  },

  onInput: function (e) {
    const patch = {}
    patch[e.currentTarget.dataset.field] = e.detail.value
    this.setData(patch)
  },

  chooseImage: function () {
    const self = this
    mp.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      success: function (res) {
        self.setData({ verifyImg: res.tempFilePaths[0] })
      }
    })
  },

  removeImage: function () {
    this.setData({ verifyImg: '' })
  },

  submit: function () {
    const self = this
    const d = this.data
    if (!d.schoolId) {
      toast('请选择你的学校')
      return
    }
    if (!d.studentNo.trim()) {
      toast('请填写学号')
      return
    }
    if (!d.realName.trim()) {
      toast('请填写真实姓名')
      return
    }
    if (!d.verifyImg) {
      toast('请上传学生证或校园卡照片')
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })

    http.post('/user/verify', {
      schoolId: d.schoolId,
      studentNo: d.studentNo.trim(),
      realName: d.realName.trim(),
      verifyImg: d.verifyImg
    }).then(function (res) {
      // 认证后 token 内含最新身份信息，直接替换
      if (res.token) {
        mp.setStorageSync('token', res.token)
        getApp().globalData.token = res.token
      }
      return http.get('/user/me', null, { silent: true }).then(function (me) {
        auth.updateUser({
          id: me.id,
          nickname: me.nickname,
          avatar: me.avatar,
          schoolId: me.schoolId,
          verifyStatus: me.verifyStatus
        })
        self.setData({
          submitting: false,
          done: me.verifyStatus === 1 || me.verifyStatus === 2,
          doneStatus: me.verifyStatus,
          alreadyVerified: me.verifyStatus === 2
        })
        toast(res.message || '提交成功')
      })
    }).catch(function () {
      self.setData({ submitting: false })
    })
  },

  goBack: function () {
    mp.navigateBack()
  },

  stop: function () {}
})
