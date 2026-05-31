import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, type LoginRequest, type LoginResponse } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const username = ref<string>('')
  const role = ref<string>('')
  const avatarUrl = ref<string>('')
  const githubLogin = ref<string>('')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isGithubLinked = computed(() => !!githubLogin.value)

  // 初始化时从 localStorage 恢复
  function init() {
    const savedToken = localStorage.getItem('prism_token')
    const savedUsername = localStorage.getItem('prism_username')
    const savedRole = localStorage.getItem('prism_role')
    const savedAvatarUrl = localStorage.getItem('prism_avatar_url')
    const savedGithubLogin = localStorage.getItem('prism_github_login')
    if (savedToken) {
      token.value = savedToken
      username.value = savedUsername || ''
      role.value = savedRole || ''
      avatarUrl.value = savedAvatarUrl || ''
      githubLogin.value = savedGithubLogin || ''
    }
  }

  function persist(data: LoginResponse) {
    token.value = data.token
    username.value = data.username
    role.value = data.role
    avatarUrl.value = data.avatarUrl || ''
    githubLogin.value = data.githubLogin || ''
    localStorage.setItem('prism_token', data.token)
    localStorage.setItem('prism_username', data.username)
    localStorage.setItem('prism_role', data.role)
    if (data.avatarUrl) {
      localStorage.setItem('prism_avatar_url', data.avatarUrl)
    } else {
      localStorage.removeItem('prism_avatar_url')
    }
    if (data.githubLogin) {
      localStorage.setItem('prism_github_login', data.githubLogin)
    } else {
      localStorage.removeItem('prism_github_login')
    }
  }

  async function login(payload: LoginRequest) {
    const data = await apiLogin(payload)
    persist(data)
    return data
  }

  function setLoginData(data: LoginResponse) {
    persist(data)
  }

  function logout() {
    token.value = ''
    username.value = ''
    role.value = ''
    avatarUrl.value = ''
    githubLogin.value = ''
    localStorage.removeItem('prism_token')
    localStorage.removeItem('prism_username')
    localStorage.removeItem('prism_role')
    localStorage.removeItem('prism_avatar_url')
    localStorage.removeItem('prism_github_login')
  }

  init()

  return {
    token,
    username,
    role,
    avatarUrl,
    githubLogin,
    isLoggedIn,
    isAdmin,
    isGithubLinked,
    login,
    setLoginData,
    logout,
  }
})
