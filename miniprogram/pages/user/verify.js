const http = require('../../utils/request')
const upload = require('../../utils/upload')
const { mp, toast } = require('../../utils/util')
const auth = require('../../utils/auth')
const schoolMod = require('../../utils/school')
const CONFIG = require('../../utils/config')

/** 微信学生身份验证插件 appid（官方固定值，不要改） */
const STUDENT_PLUGIN_ID = 'wxa16657d57059e0f0'

/** 后端 LocalDateTime 是 ISO 字符串（2026-11-03T10:20:30），转成「2026-11-03 10:20」 */
function fmtTime(at) {
  if (!at) return ''
  return String(at).replace('T', ' ').slice(0, 16)
}

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
    /** 照片上传中：锁住重复选择，也告诉用户「点了是有反应的」 */
    uploading: false,
    showPicker: false,
    /**
     * 结果态开关。
     * null = 还没拉到认证状态（此时两个分支都不渲染，避免已认证用户先闪一下表单）
     * true = 展示结果卡片 / false = 展示表单
     */
    done: null,
    doneStatus: 0,
    alreadyVerified: false,

    /* ===== 认证状态回显（已认证卡片 / 驳回原因 / 重认证） ===== */
    /** 认证来源 1学生证 2微信，已认证卡片上展示 */
    verifySource: 0,
    /** status=3 时后端给的驳回原因，用户可见 */
    rejectReason: '',
    /** true = 已认证用户点开了重新认证，此时不显示结果卡片、显示表单 */
    reapplyOpen: false,
    /** 重认证冷却中：可再次申请的时间文案（"2026-11-03 10:20"），空串表示可以申请 */
    reapplyAt: ''
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
    // 登录返回的用户对象只有 brief 字段（id/nickname/avatar/schoolId/verifyStatus），
    // 认证页要展示学校名、认证方式、驳回原因、冷却时间，必须单独拉一次 /user/me
    auth.ensureLogin().then(function () {
      return self.loadMe()
    }).catch(function () {
      // 拉不到认证状态（拒绝登录 / 接口异常）就按「未认证」展示表单，别让人对着空白页
      self.setData({ done: false })
      self.loadSchools()
    })
    this.checkPluginUpdate()
  },

  /**
   * 拉当前认证状态并存进 data。
   * silent 静默请求：这页已有登录态，失败时让用户看到表单，比弹错误框好。
   */
  loadMe: function () {
    const self = this
    return http.get('/user/me', null, { silent: true }).then(function (me) {
      self.applyMe(me)
      self.loadSchools()
    }).catch(function () {
      self.setData({ done: false })
      self.loadSchools()
    })
  },

  /** 把 /user/me 的结果映射成页面状态（onLoad 与提交成功后共用） */
  applyMe: function (me) {
    const st = me.verifyStatus || 0
    this.setData({
      schoolId: me.schoolId || null,
      schoolName: me.schoolName || '',
      verifySource: me.verifySource || 0,
      // 驳回原因只在「驳回」状态有意义：审核中/已认证时 remark 是后台内部备注，
      // 当原因展示会误导用户（例如「重认证申请：原学校 XX」）
      rejectReason: st === 3 ? (me.verifyRemark || '') : '',
      done: st === 1 || st === 2,
      doneStatus: st,
      alreadyVerified: st === 2,
      reapplyAt: fmtTime(me.reapplyAt)
    })
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

  /**
   * 选择认证材料，**选完立刻上传**。
   *
   * ⚠️ 这里必须先上传、把服务端返回的 URL 存进 verifyImg：wx.chooseImage 给的是
   * `wxfile://tmp_xxx.jpg` 这种**本机临时文件路径**，只在当前小程序生命周期内有效。
   * 直接拿它当材料提交，写进 t_user.verify_img 的就是一串本机 path ——
   * 后台「用户详情 → 认证材料」拿它在浏览器里渲染，永远只会显示「加载失败」，
   * 而且审核员连材料长什么样都看不到。历史上已经有 2 个账号是这样脏进去的。
   *
   * 失败要回滚预览：不能留一张本地预览图在那里骗人 —— 预览有图、提交无图。
   */
  chooseImage: function () {
    const self = this
    if (this.data.uploading) return
    mp.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      success: function (res) {
        const path = res.tempFilePaths && res.tempFilePaths[0]
        if (!path) return
        const prev = self.data.verifyImg
        // 先拿本地路径顶上预览（秒回），后台自动压到 5MB 以内
        self.setData({ verifyImg: path, uploading: true })
        mp.showLoading({ title: '上传中', mask: true })
        upload.uploadImage(path).then(function (url) {
          mp.hideLoading()
          self.setData({ verifyImg: url, uploading: false })
        }).catch(function () {
          mp.hideLoading()
          self.setData({ verifyImg: prev, uploading: false })
          toast('照片上传失败，请重试')
        })
      }
    })
  },

  /**
   * 提交前兜底：确认 verifyImg 已经是服务端 URL。
   *
   * 正常流程下 chooseImage 已经上传过了，这里是防两条漏网之鱼：
   * ① 上传失败但页面没回滚干净的极端情况；② 以后有人改了 chooseImage 忘了同步。
   * 拦在前端比让脏数据落库再清理便宜得多（后端 VerifyReq 另有一道 Pattern 校验兜同样的底）。
   */
  ensureImgUploaded: function () {
    const img = this.data.verifyImg
    if (!img) {
      return Promise.reject({ code: 400, msg: '请上传学生证或校园卡照片' })
    }
    if (upload.isServerUrl(img)) {
      return Promise.resolve(img)
    }
    const self = this
    mp.showLoading({ title: '上传中', mask: true })
    return upload.uploadImage(img).then(function (url) {
      mp.hideLoading()
      self.setData({ verifyImg: url })
      return url
    }).catch(function (e) {
      mp.hideLoading()
      toast('照片上传失败，请重试')
      return Promise.reject(e)
    })
  },

  removeImage: function () {
    this.setData({ verifyImg: '' })
  },

  /* ==================== 重新认证 ==================== */

  /**
   * 已认证用户点「重新认证」。
   *
   * 冷却期没到就直接提示剩余时间，不进表单——否则用户填完材料、传完照片，
   * 提交时才被后端拦下，白折腾一套。
   */
  openReapply: function () {
    if (this.data.reapplyAt) {
      toast('重认证冷却中，' + this.data.reapplyAt + ' 后可再次申请')
      return
    }
    this.setData({
      done: false,
      reapplyOpen: true,
      // 学号/姓名是加密字段，接口只返回脱敏值，重认证必须重新填；
      // 这里清空，避免把上次残留的输入带进来
      studentNo: '',
      realName: '',
      verifyImg: ''
    })
  },

  /** 放弃重认证，回到认证结果卡片 */
  cancelReapply: function () {
    this.setData({ reapplyOpen: false, done: this.data.doneStatus === 1 || this.data.doneStatus === 2 })
    // 重新拉一次：表单里可能改过学校但没提交，卡片不能显示成新学校
    this.loadMe()
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
    // 材料必须先落到服务端：本地 wxfile:// 路径提交上去，后台看到的就是一张破图
    this.ensureImgUploaded().then(function (url) {
      return http.post('/user/verify', {
        schoolId: d.schoolId,
        studentNo: d.studentNo.trim(),
        realName: d.realName.trim(),
        verifyImg: url
      })
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
      // 提交成功后清掉表单临时输入：照片是临时路径，留着下次容易误传旧图
      self.setData({
        submitting: false,
        wxSubmitting: false,
        showWxVerify: false,
        reapplyOpen: false,
        studentNo: '',
        realName: '',
        verifyImg: ''
      })
      self.applyMe(me)
      toast((res && res.message) || '提交成功')
    })
  },

  /**
   * 进注销页。
   * 认证中心放这个入口，是因为「要改认证信息」和「要彻底离开」是同一批用户；
   * 真正的后果说明与勾选确认都在 cancel 页，这里只负责跳转。
   */
  goCancel: function () {
    mp.navigateTo({ url: '/pages/user/cancel' })
  },

  goBack: function () {
    mp.navigateBack()
  },

  stop: function () {}
})
