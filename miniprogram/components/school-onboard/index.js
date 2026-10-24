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

Component({
  properties: {
    /** 是否显示 */
    show: { type: Boolean, value: false },
    /** 父页面正在提交（禁止重复点选） */
    saving: { type: Boolean, value: false }
  },

  data: {
    schools: [],
    filtered: [],
    keyword: '',
    loading: false,
    /** 已点选、等待父页面落库的学校 id（字符串） */
    pickedId: ''
  },

  observers: {
    show: function (visible) {
      // 首次打开才拉列表，之后复用；失败不重试（可关掉重开）
      if (visible && !this.data.schools.length && !this.data.loading) {
        this.loadSchools()
      }
    }
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
        self.setData({ schools: arr, filtered: arr, loading: false })
      }).catch(function () {
        self.setData({ loading: false })
        toast('学校列表加载失败，请稍后重试')
      })
    },

    onKeyword: function (e) {
      const kw = (e.detail.value || '').trim()
      const all = this.data.schools
      const filtered = kw
        ? all.filter(function (s) {
          return (s.name && s.name.indexOf(kw) >= 0) || (s.city && s.city.indexOf(kw) >= 0)
        })
        : all
      this.setData({ keyword: kw, filtered: filtered })
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
