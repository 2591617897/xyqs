Component({
  properties: {
    src: { type: String, value: '' },
    size: { type: Number, value: 72 }
  },
  data: { failed: false },
  methods: {
    onErr: function () { this.setData({ failed: true }) }
  }
})
