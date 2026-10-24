/**
 * 左滑操作单元格（swipe-cell）
 *
 * 用法：
 *   <swipe-cell bind:action="onDelete">
 *     <view class="your-content">...</view>
 *   </swipe-cell>
 *
 * 说明：
 *   1. 手指左滑露出右侧操作区，右滑收起；横向滑动超过阈值即判定为意图操作。
 *   2. 竖向滑动（列表滚动）不被拦截：只有横向位移占优时才接管手势。
 *   3. 展开状态由父组件通过 open 属性控制（受控模式），
 *      保证「同时只有一个卡片处于展开态」由页面统一管理。
 *   4. 点击操作区按钮触发 action 事件，参数为按钮的 data-action。
 */
Component({
  options: {
    multipleSlots: true,
    addGlobalClass: true
  },

  properties: {
    /** 元素 id：透传到根节点，供 scroll-into-view 定位 */
    swipeId: {
      type: String,
      value: ''
    },
    /** 是否处于展开状态（受控：由父组件维护，保证同时只展开一个） */
    open: {
      type: Boolean,
      value: false
    },
    /** 操作区宽度（px），需与 wxss 中的 .sc-actions 宽度一致 */
    actionWidth: {
      type: Number,
      value: 88
    },
    /** 判定为「想操作」的最小横向位移（px） */
    trigger: {
      type: Number,
      value: 20
    }
  },

  data: {
    /** 当前横向位移（px），负值表示已左移 */
    offset: 0,
    /** 是否正在手势中（手势中禁用过渡动画，保证跟手） */
    dragging: false
  },

  lifetimes: {
    attached() {
      this._startX = 0
      this._startY = 0
      this._moved = false
      this._horizontal = false
    }
  },

  observers: {
    /** 父组件改变 open 时同步位移（手势中不打断） */
    open(v) {
      if (this.data.dragging) return
      this.setData({ offset: v ? -this.properties.actionWidth : 0 })
    }
  },

  methods: {
    onTouchStart(e) {
      const t = e.touches && e.touches[0]
      if (!t) return
      this._startX = t.clientX
      this._startY = t.clientY
      this._moved = false
      this._horizontal = false
      // 记录起始位移：已展开时从展开位置继续拖
      this._baseOffset = this.data.offset
    },

    onTouchMove(e) {
      const t = e.touches && e.touches[0]
      if (!t) return
      const dx = t.clientX - this._startX
      const dy = t.clientY - this._startY

      if (!this._moved) {
        // 位移太小时不下结论，避免与滚动/点击冲突
        if (Math.abs(dx) < 6 && Math.abs(dy) < 6) return
        this._moved = true
        // 横向位移占优 → 判定为左滑意图；否则交给页面滚动，不拦截
        this._horizontal = Math.abs(dx) > Math.abs(dy)
        if (!this._horizontal) return
        this.setData({ dragging: true })
      }
      if (!this._horizontal) return

      let next = this._baseOffset + dx
      const max = -this.properties.actionWidth
      if (next > 0) next = 0
      if (next < max) next = max
      this.setData({ offset: next })
    },

    onTouchEnd() {
      if (!this._moved || !this._horizontal) {
        this.setData({ dragging: false })
        return
      }
      this._moved = false
      this._horizontal = false

      const half = this.properties.actionWidth / 2
      const shouldOpen = this.data.offset <= -half
      this.setData({ dragging: false, offset: shouldOpen ? -this.properties.actionWidth : 0 })
      this.triggerEvent('openchange', { open: shouldOpen })
    },

    /** 点击操作区按钮 */
    onActionTap(e) {
      const action = e.currentTarget.dataset.action || ''
      this.triggerEvent('action', { action })
    }
  }
})
