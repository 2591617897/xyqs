const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const schoolMod = require('../../utils/school')

Page({
  data: {
    schools: [],
    // 选择器里渲染的列表（含「同城推荐 / 全部学校」分组标题）
    list: [],
    keyword: '',
    /** IP 定位到的城市，用于把同城学校提到最前；为空则不分组 */
    geoCity: '',
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
      self.setData({ schools: list || [] })
      self.render()
    }).catch(function () {
      toast('学校列表加载失败')
    })
  },

  /** 按当前关键词 + 定位城市重算选择器列表 */
  render: function () {
    const kw = this.data.keyword
    const all = this.data.schools || []
    if (kw) {
      // 搜索时不分组：用户已经在缩小范围，再插标题反而碍事
      this.setData({ list: schoolMod.flatList(schoolMod.search(all, kw)) })
      return
    }
    this.setData({ list: schoolMod.buildList(all, this.data.geoCity) })
  },

  /**
   * IP 定位（后端读请求来源 IP，用户无感，不需要授权）。
   * 拿到城市就把同城学校提到最前；拿不到就维持原始顺序，不做任何提示。
   * 结果在 utils/school 里做进程内缓存，重复打开弹层不会重复请求。
   */
  ensureIpCity: function () {
    const self = this
    schoolMod.ipCity().then(function (city) {
      if (!city || city === self.data.geoCity) return
      self.setData({ geoCity: city })
      self.render()
    })
  },

  openPicker: function () {
    this.setData({ showPicker: true, keyword: '' })
    this.render()
    this.ensureIpCity()
  },

  closePicker: function () {
    this.setData({ showPicker: false })
  },

  onKeyword: function (e) {
    this.setData({ keyword: e.detail.value.trim() })
    this.render()
  },

  pickSchool: function (e) {
    // id 统一按字符串比较，避免雪花 ID 精度丢失
    const id = e.currentTarget.dataset.id
    const school = this.data.schools.filter(function (s) {
      return String(s.id) === String(id)
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
        auth.markTokenFresh()
      }
      return http.get('/user/me', null, { silent: true }).then(function (me) {
        auth.updateUser({
          id: me.id,
          nickname: me.nickname,
          avatar: me.avatar,
          schoolId: me.schoolId,
          schoolName: me.schoolName || '',
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
