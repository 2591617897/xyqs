const http = require('../../utils/request')
const adMod = require('../../utils/ad')

/**
 * 通用广告位。
 *
 * <p>页面里一行 `&lt;ad-banner code="wall_top" /&gt;` 就够：组件自己按 code 拉素材、
 * 自己读当前城市做定向、自己上报点击。素材为空时<b>整块不渲染</b>，
 * 后台没配广告的页面不会留一个空白条。
 *
 * <p>code 必须与后台 campus-admin 的 {@code AdSlotCatalog} 一致，
 * 填错不会报错、只是永远拿不到数据——所以新增投放位时两边要一起加。
 */
Component({
  properties: {
    /** 广告位编码，与后台注册表一一对应 */
    code: { type: String, value: '' },
    /** 展示高度（rpx），默认 240；首页那种大图自己在页面里做，不用本组件 */
    height: { type: Number, value: 240 },
    /**
     * 左右是否自带 24rpx 边距。
     * 页面自己没有横向 padding 时保持 true；像「功能区」这种 .page 已有内边距，
     * 传 false 才能和下面的卡片左右对齐。
     */
    inset: { type: Boolean, value: true }
  },

  data: {
    list: []
  },

  attached: function () {
    this.load()
  },

  methods: {
    load: function () {
      const self = this
      const code = this.data.code
      if (!code) return
      // 城市定向：未登录 / 未选学校时为空，那种情况只拿得到「全国」投放的广告
      const app = getApp()
      const user = (app && app.globalData && app.globalData.user) || {}
      const city = user.schoolCity || ''
      http.get('/ads/' + code, { city: city }, { silent: true }).then(function (list) {
        self.setData({ list: (list || []).slice(0, 5) })
      }).catch(function () {
        // 运营位是锦上添花，失败就当没配，不要影响页面主体
      })
    },

    onTap: function (e) {
      const ad = e.currentTarget.dataset.ad
      if (!ad) return
      adMod.reportClick(ad.id)
      adMod.open(ad)
    },

    /**
     * 图片加载失败就摘掉这一条。
     * 常见原因是素材图被删、或库里存的是局域网地址（真机 <image> 加载 http://192.168.x.x 会静默失败）。
     */
    onImgError: function (e) {
      const idx = e.currentTarget.dataset.index
      if (idx == null) return
      const list = (this.data.list || []).slice()
      if (idx < 0 || idx >= list.length) return
      list.splice(idx, 1)
      this.setData({ list: list })
    }
  }
})
