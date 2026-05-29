import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, type LoginRequest } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const username = ref<string>('')
  const role = ref<string>('')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  // 初始化时从 localStorage 恢复
  function init() {
    const savedToken = localStorage.getItem('prism_token')
    const savedUsername = localStorage.getItem('prism_username')
    const savedRole = localStorage.getItem('prism_role')
    if (savedToken) {
      token.value = savedToken
      username.value = savedUsername || ''
      role.value = savedRole || ''
    }
  }

  async function login(payload: LoginRequest) {
    const data = await apiLogin(payload)
    token.value = data.token
    username.value = data.username
    role.value = data.role
    localStorage.setItem('prism_token', data.token)
    localStorage.setItem('prism_username', data.username)
    localStorage.setItem('prism_role', data.role)
    return data
  }

  function logout() {
    token.value = ''
    username.value = ''
    role.value = ''
    localStorage.removeItem('prism_token')
    localStorage.removeItem('prism_username')
    localStorage.removeItem('prism_role')
  }

  init()

  return {
    token,
    username,
    role,
    isLoggedIn,
    isAdmin,
    login,
    logout,
  }
})
