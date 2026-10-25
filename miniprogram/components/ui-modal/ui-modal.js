Component({
  properties: {
    visible: { type: Boolean, value: false },
    title: { type: String, value: '' },
    content: { type: String, value: '' },
    confirmText: { type: String, value: '确定' },
    cancelText: { type: String, value: '取消' },
    maskClosable: { type: Boolean, value: true }
  },
  methods: {
    stop: function () {},
    onMask: function () {
      if (this.data.maskClosable) this.triggerEvent('cancel')
    },
    onCancel: function () { this.triggerEvent('cancel') },
    onOk: function () { this.triggerEvent('ok') }
  }
})
