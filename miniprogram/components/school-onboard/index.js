/**
 * 新用户引导：选择所在学校。
 *
 * 背景：静默登录建号时 schoolId 为 null，而后端「本校」筛选是
 * `.eq(schoolId != null, ...)`——为空时等于不过滤，新用户会看到全站内容。
 * 所以首次进入必须让用户落一个学校（不要求学籍认证，认证仍走 pages/user/verify）。
 *
 * 组件只负责「选」，落库由页面调 PUT /user/me。
 */
const http = require('../../utils/request')
const { toast } = require('../../utils/util')
const schoolMod = require('../../utils/school')

Component({
  properties: {
    /** 是否显示 */
    show: { type: Boolean, value: false },
    /** 父页面正在提交（禁止重复点选） */
    saving: { type: Boolean, value: false }
  },

  data: {
    schools: [],
    // 渲染列表（含「同城推荐 / 全部学校」分组标题）
    list: [],
    keyword: '',
    /** IP 定位到的城市；为空则不分组 */
    geoCity: '',
    loading: false,
    /** 已点选、等待父页面落库的学校 id（字符串） */
    pickedId: ''
  },

  observers: {
    show: function (visible) {
      if (!visible) return
      // 首次打开才拉列表，之后复用；失败不重试（可关掉重开）
      if (!this.data.schools.length && !this.data.loading) {
        this.loadSchools()
      } else {
        this.ensureIpCity()
      }
    }
  },

  attached: function () {
    // 提前把定位城市准备好：ipCity() 命中本地缓存时是同步 Promise，
    // 这样首次 render 就能直接出「同城置顶」，避免列表先分组再跳一下。
    const self = this
    schoolMod.ipCity().then(function (city) {
      if (city) self.setData({ geoCity: city })
    })
  },

  methods: {
    loadSchools: function () {
      const self = this
      this.setData({ loading: true })
      http.get('/schools', null, { silent: true }).then(function (list) {
        // id 统一转成字符串副本：后端雪花 ID 是字符串、mock 是数字，
        // 直接比较会因类型不同导致选中态失效
        const arr = (list || []).map(function (s) {
          return Object.assign({}, s, { _key: String(s.id) })
        })
        self.setData({ schools: arr, loading: false })
        self.render()
        self.ensureIpCity()
      }).catch(function () {
        self.setData({ loading: false })
        toast('学校列表加载失败，请稍后重试')
      })
    },

    /** 按当前关键词 + 定位城市重算列表 */
    render: function () {
      const all = this.data.schools
      const kw = this.data.keyword
      if (kw) {
        // 搜索不分组：用户已经在缩小范围，再插标题反而碍事
        this.setData({ list: schoolMod.flatList(schoolMod.search(all, kw)) })
        return
      }
      this.setData({ list: schoolMod.buildList(all, this.data.geoCity) })
    },

    /** IP 定位（后端解析请求来源 IP，用户无感）：同城学校提到最前，失败静默 */
    ensureIpCity: function () {
      const self = this
      schoolMod.ipCity().then(function (city) {
        if (!city || city === self.data.geoCity) return
        self.setData({ geoCity: city })
        self.render()
      })
    },

    onKeyword: function (e) {
      this.setData({ keyword: (e.detail.value || '').trim() })
      this.render()
    },

    onPick: function (e) {
      if (this.data.saving) return
      const id = String(e.currentTarget.dataset.id)
      const school = this.data.schools.filter(function (s) {
        return String(s.id) === id
      })[0]
      if (!school) return
      this.setData({ pickedId: id })
      this.triggerEvent('select', { school: school })
    },

    onSkip: function () {
      if (this.data.saving) return
      this.triggerEvent('close')
    },

    /** 阻止点击抽屉内容时冒泡到遮罩 */
    noop: function () {}
  }
})
