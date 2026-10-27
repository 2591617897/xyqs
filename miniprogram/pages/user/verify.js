const http = require('../../utils/request')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const schoolMod = require('../../utils/school')
const CONFIG = require('../../utils/config')

/** 微信学生身份验证插件 appid（官方固定值，不要改） */
const STUDENT_PLUGIN_ID = 'wxa16657d57059e0f0'

Page({
  data: {
    /**
     * 认证方式：wx 微信学生认证（免材料）/ manual 学生证材料（人工审核）。
     * 插件没开通时 wxEnabled=false，页面直接退回单通道，不会出现「点了没反应」。
     */
    mode: 'wx',
    wxEnabled: CONFIG.WX_STUDENT_VERIFY === true,
    showWxVerify: false,
    wxSubmitting: false,

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

  /** code 只在内存里放一份：插件 auth 与 close 都会回调，靠它去重 */
  wxCode: '',
  /** 一次性提交锁：防止 auth + close 双触发导致同码提交两次（第二次必然报 code 已用） */
  wxSubmitted: false,

  onLoad: function () {
    mp.setNavigationBarTitle({ title: '校园身份认证' })
    // 插件没开通时默认落到学生证通道，避免用户看到一个点不动的按钮
    if (!this.data.wxEnabled) {
      this.setData({ mode: 'manual' })
    }
    const self = this
    auth.ensureLogin().then(function (user) {
      if (user && user.verifyStatus === 2) {
        self.setData({ alreadyVerified: true, done: true, doneStatus: 2 })
      }
      self.loadSchools()
    }).catch(function () {
      self.loadSchools()
    })
    this.checkPluginUpdate()
  },

  /**
   * 插件版本检查。
   *
   * 官方要求 version 必须写 latest，并自己接自动更新：低版本插件会被微信停用，
   * 停用后的表现是「弹层能开、点了没反应」，不接这段很难排查。
   */
  checkPluginUpdate: function () {
    if (!this.data.wxEnabled) return
    if (!wx.getPluginUpdateManager || !wx.getUpdateManager) return
    try {
      const pluginManager = wx.getPluginUpdateManager({ pluginId: STUDENT_PLUGIN_ID })
      const weappManager = wx.getUpdateManager()
      pluginManager.onUpdateReady(function () {
        // 插件热更需要重启小程序才生效
        weappManager.applyUpdate()
      })
      pluginManager.onUpdateFailed(function () {
        console.warn('[verify] 学生验证插件更新失败，可尝试重启小程序')
      })
    } catch (e) {
      console.warn('[verify] 插件更新检查失败', e)
    }
  },

  switchMode: function (e) {
    const mode = e.currentTarget.dataset.mode
    if (mode === this.data.mode) return
    this.setData({ mode: mode })
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

  /* ==================== 微信学生认证 ==================== */

  /**
   * 拉起微信授权弹层。
   *
   * 学校必须先选：微信只返回「是不是学生」，不返回学校，
   * 而本站的「同校」筛选依赖 schoolId，没学校等于认证完也不知道属于哪儿。
   */
  startWxVerify: function () {
    if (!this.data.wxEnabled) {
      toast('微信学生认证暂未开通，请使用学生证认证')
      return
    }
    if (!this.data.schoolId) {
      toast('请先选择你的学校')
      return
    }
    this.wxCode = ''
    this.wxSubmitted = false
    this.setData({ showWxVerify: true })
  },

  onStudentVerifyAuth: function (e) {
    const code = e && e.detail ? e.detail.wx_studentcheck_code : ''
    if (!code) return
    this.wxCode = code
    this.submitWx(code)
  },

  /**
   * 用户关闭弹层。
   *
   * 官方文档明确：auth 与 close 没有必然关系，可能只有 close 而 code 已经在
   * auth 里给过（也可能完全没给）。所以这里只做「有 code 且没提交过」的兜底，
   * 不要假设 auth 一定先到。
   */
  onStudentVerifyClose: function () {
    this.setData({ showWxVerify: false })
    const code = this.wxCode
    if (code && !this.wxSubmitted) {
      this.submitWx(code)
    }
  },

  onStudentVerifyReject: function () {
    this.setData({ showWxVerify: false })
    toast('你取消了微信认证，可改用学生证认证')
  },

  submitWx: function (code) {
    if (this.wxSubmitted) return
    this.wxSubmitted = true
    const self = this
    this.setData({ wxSubmitting: true })
    http.post('/user/verify/wx-student', {
      schoolId: this.data.schoolId,
      code: code
    }).then(function (res) {
      // code 已用掉，不管成功失败都不能再拿它重试
      self.wxCode = ''
      return self.applyVerified(res)
    }).catch(function () {
      // 失败允许重新走一次授权（code 可能过期，重拉一次即可）
      self.wxSubmitted = false
      self.wxCode = ''
      self.setData({ wxSubmitting: false })
    })
  },

  /* ==================== 学生证材料 ==================== */

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
      return self.applyVerified(res)
    }).catch(function () {
      self.setData({ submitting: false })
    })
  },

  /**
   * 认证结果统一收口（两条通道共用）。
   *
   * 认证后 JWT 里的 schoolId / verifyStatus 是旧快照，必须换 token 并刷新缓存，
   * 否则「我的」页仍显示未认证，发布入口也还是锁着的。
   */
  applyVerified: function (res) {
    const self = this
    if (res && res.token) {
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
        wxSubmitting: false,
        showWxVerify: false,
        done: me.verifyStatus === 1 || me.verifyStatus === 2,
        doneStatus: me.verifyStatus,
        alreadyVerified: me.verifyStatus === 2
      })
      toast((res && res.message) || '提交成功')
    })
  },

  goBack: function () {
    mp.navigateBack()
  },

  stop: function () {}
})
