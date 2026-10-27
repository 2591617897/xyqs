/**
 * 微信学生身份验证插件的本地占位组件。
 *
 * 为什么需要它：认证页 WXML 里有一行
 *   <studentVerify show isQuick bind:auth ...>
 * 插件没开通时（app.json 不声明 plugins），这个标签就找不到组件实现。
 * 虽然多数情况下只是控制台告警，但项目的 `lazyCodeLoading: requiredComponents`
 * 下出现「组件不存在」并不值得赌——所以用一个同名占位组件把解析兜住：
 *
 *   · 未开通：pages/user/verify.json 指向本组件，渲染为空，用户看不到任何东西
 *   · 已开通：scripts/wx-student-plugin.js 把它改成 plugin://studentVerify/verify
 *
 * 页面里该标签外层还有 wx:if="{{wxEnabled}}"，未开通时根本不会实例化。
 */
Component({
  properties: {
    /** 插件用：是否显示授权弹层。占位组件不处理，保留属性避免告警 */
    show: {
      type: Boolean,
      value: false
    },
    /** 插件用：是否走「快速验证」场景 */
    isQuick: {
      type: Boolean,
      value: true
    }
  },

  /** 占位组件不渲染任何内容，也不触发 auth / reject / close 事件 */
  methods: {}
})
