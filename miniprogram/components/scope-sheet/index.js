Component({
  properties: {
    /** 是否显示弹层 */
    show: { type: Boolean, value: false },
    /** 认证学校名（本校选项标题） */
    schoolName: { type: String, value: '' },
    /** 学校所在市（本市选项标题） */
    schoolCity: { type: String, value: '' },
    /** 位置 API 解析到的当前定位城市 {city, district}，解析不到传 null */
    geoCity: { type: Object, value: null },
    /** 当前范围：school / city */
    current: { type: String, value: 'school' },
    /** 当前城市（city 模式下生效，用于高亮定位项） */
    currentCity: { type: String, value: '' }
  },

  methods: {
    noop: function () {},

    onClose: function () {
      this.triggerEvent('close')
    },

    onPickSchool: function () {
      this.triggerEvent('select', { scope: 'school', city: '' })
    },

    onPickCity: function () {
      this.triggerEvent('select', { scope: 'city', city: this.data.schoolCity || '' })
    },

    onPickGeo: function () {
      const geo = this.data.geoCity || {}
      this.triggerEvent('select', { scope: 'city', city: geo.city || '' })
    },

    /** 学籍认证 / 切换学校 */
    onGoVerify: function () {
      this.triggerEvent('verify')
    }
  }
})
