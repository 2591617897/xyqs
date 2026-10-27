const http = require('../../../utils/request')
const { mp, toast } = require('../../../utils/util')

const PAGE_SIZE = 30

Page({
  data: {
    statusBarHeight: 20,
    groupId: '',
    /**
     * true = 选人模式（点了通过 eventChannel 回传，不跳资料页）。
     * 群聊页的 @ 已改成页内面板（不跳页，输入框焦点不丢），
     * 这里保留该模式给其他需要「从成员列表挑一个人」的入口复用。
     */
    selectMode: false,
    keyword: '',
    list: [],
    page: 1,
    hasMore: true,
    loading: false,
    total: 0
  },

  onLoad: function (opt) {
    const info = typeof mp.getWindowInfo === 'function' ? mp.getWindowInfo() : mp.getSystemInfoSync()
    this.setData({
      statusBarHeight: info.statusBarHeight || 20,
      groupId: String((opt && opt.id) || ''),
      selectMode: !!(opt && opt.select === '1')
    })
    this.load(true)
  },

  onReachBottom: function () {
    if (this.data.hasMore && !this.data.loading) this.load(false)
  },

  load: function (reset) {
    const self = this
    if (this.data.loading) return
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true })
    http.get('/groups/' + this.data.groupId + '/members', {
      keyword: this.data.keyword || '',
      page: page,
      size: PAGE_SIZE
    }, { silent: true }).then(function (res) {
      const records = (res && res.records) || []
      const list = reset ? records : self.data.list.concat(records)
      self.setData({
        list: list,
        page: page,
        total: (res && res.total) || list.length,
        hasMore: !!(res && res.hasMore),
        loading: false
      })
    }).catch(function () {
      self.setData({ loading: false })
    })
  },

  onSearch: function (e) {
    const kw = e.detail.value
    this.setData({ keyword: kw })
    // 简单防抖：一万人群里每按一个字就打接口会很浪费
    if (this._timer) clearTimeout(this._timer)
    const self = this
    this._timer = setTimeout(function () {
      self.load(true)
    }, 300)
  },

  onClear: function () {
    this.setData({ keyword: '' })
    this.load(true)
  },

  onTap: function (e) {
    const item = e.currentTarget.dataset.item
    if (!item) return
    if (this.data.selectMode) {
      // 通过 eventChannel 把选中的人回传给群聊页
      const ec = this.getOpenerEventChannel && this.getOpenerEventChannel()
      if (ec && ec.emit) ec.emit('pickMember', { id: item.userId, nickname: item.nickname })
      mp.navigateBack()
      return
    }
    mp.navigateTo({ url: '/pages/user/space/space?id=' + item.userId })
  },

  goBack: function () {
    mp.navigateBack()
  },

  onUnload: function () {
    if (this._timer) clearTimeout(this._timer)
  }
})
