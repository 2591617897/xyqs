import { defineStore } from 'pinia'
import { getToken, setToken, clearToken } from '@/utils/request'
import { authApi } from '@/api/system'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    info: null,
    permissions: [],
    menus: []
  }),
  getters: {
    isSuper: (s) => !!(s.info && s.info.superFlag),
    username: (s) => (s.info && (s.info.realName || s.info.username)) || '',
    mustChangePwd: (s) => !!(s.info && s.info.mustChangePwd)
  },
  actions: {
    async login (data) {
      const res = await authApi.login(data)
      if (res && res.token) {
        this.token = res.token
        setToken(res.token)
      }
      return res
    },
    async loadInfo (force = false) {
      if (this.info && !force) return this.info
      const info = await authApi.info()
      this.info = info
      this.permissions = (info && info.permissions) || []
      return info
    },
    async loadMenus (force = false) {
      if (this.menus && this.menus.length && !force) return this.menus
      const menus = await authApi.menus()
      this.menus = menus || []
      return this.menus
    },
    reset () {
      this.token = ''
      this.info = null
      this.permissions = []
      this.menus = []
      clearToken()
    }
  }
})
