import { defineStore } from 'pinia'
import { mockApi } from '@/api/mock'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null')
  }),

  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.userInfo?.role === 1
  },

  actions: {
    async login(username, password) {
      const res = await mockApi.login(username, password)
      if (res.code === 200) {
        this.token = res.data.token
        this.userInfo = res.data.userInfo
        localStorage.setItem('token', this.token)
        localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
      }
      return res
    },

    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }
})
