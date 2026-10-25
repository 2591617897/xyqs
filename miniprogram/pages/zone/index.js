const http = require('../../utils/request')
const { mp } = require('../../utils/util')
const zoneMod = require('../../utils/zones')

/**
 * 功能区（底部 tab 第 2 项）
 * ------------------------------------------------------------------
 * 原先是首页顶部的「金刚区」，2026-10-25 按需求独立成页：
 *   底部「闲置」→「功能区」，首页不再放功能入口。
 *
 * 入口数据仍只有一份配置源：`utils/zones.js#buildEntries`
 *   = 3 个固定入口（我想说 / 校园闲置 / 校园频道）
 *   + 后端 t_channel 动态生成的频道入口
 * 后台加一行频道，这里自动多一个图标，不用改前端。
 */
Page({
  data: {
    entries: [],
    loading: true,
    /** 频道接口失败：固定入口照常可用，只是少了频道入口 */
    partial: false
  },

  onShow: function () {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      // hidden:false 兜底：其它页面弹层没走正常关闭路径时，别让底栏一直不显示
      this.getTabBar().setData({ selected: 1, hidden: false })
    }
    this.load()
  },

  load: function () {
    const self = this
    this.setData({ loading: true })
    http.get('/channels', null, { silent: true }).then(function (channels) {
      self.setData({
        entries: zoneMod.buildEntries(channels || []),
        loading: false,
        partial: false
      })
      mp.stopPullDownRefresh()
    }).catch(function () {
      // 频道挂了也不能白屏：固定入口是前端常量，照样渲染
      self.setData({
        entries: zoneMod.buildEntries([]),
        loading: false,
        partial: true
      })
      mp.stopPullDownRefresh()
    })
  },

  goEntry: function (e) {
    const url = e.currentTarget.dataset.url
    if (!url) return
    // 进「闲置」前清掉上一轮残留的「我的闲置/收藏」模式，
    // 否则点进来看到的是自己的列表而不是全部闲置
    if (url.indexOf('/pages/goods/list') === 0) {
      const app = getApp()
      if (app && app.globalData) app.globalData.goodsMode = ''
    }
    // 本页所有落点都不是 tab 页（闲置/表白墙/频道列表），统一 navigateTo
    mp.navigateTo({ url: url })
  },

  onPullDownRefresh: function () {
    this.load()
  },

  retry: function () {
    this.load()
  }
})
