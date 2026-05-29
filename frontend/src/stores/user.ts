import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, type LoginRequest } from '@/api/auth'

export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>('')
    const username = ref<string>('')
    const role = ref<string>('')

    const isLoggedIn = computed(() => !!token.value)
    const isAdmin = computed(() => role.value === 'ADMIN')

    async function login(payload: LoginRequest) {
      const data = await apiLogin(payload)
      token.value = data.token
      username.value = data.username
      role.value = data.role
      localStorage.setItem('prism_token', data.token)
      return data
    }

    function logout() {
      token.value = ''
      username.value = ''
      role.value = ''
      localStorage.removeItem('prism_token')
    }

    return {
      token,
      username,
      role,
      isLoggedIn,
      isAdmin,
      login,
      logout,
    }
  },
  {
    persist: {
      key: 'prism_user',
      pick: ['token', 'username', 'role'],
    },
  },
)
