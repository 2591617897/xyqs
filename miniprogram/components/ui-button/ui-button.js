Component({
  options: { multipleSlots: true },
  properties: {
    type: { type: String, value: 'primary' },
    size: { type: String, value: 'default' },
    block: { type: Boolean, value: false },
    disabled: { type: Boolean, value: false },
    loading: { type: Boolean, value: false }
  },
  methods: {
    onTap: function () {
      if (!this.data.disabled && !this.data.loading) {
        this.triggerEvent('tap')
      }
    }
  }
})
