<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { githubCallback } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  const code = route.query.code as string
  if (!code) {
    error.value = '缺少授权码，请重新登录'
    loading.value = false
    return
  }

  try {
    const data = await githubCallback(code)
    // 存储登录状态
    userStore.setLoginData(data)
    message.success('GitHub 登录成功')
    router.replace('/')
  } catch (e: any) {
    error.value = e?.message || 'GitHub 登录失败，请重试'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="callback-page">
    <a-spin v-if="loading" size="large" tip="正在通过 GitHub 登录..." />
    <a-result v-else-if="error" status="error" title="登录失败" :sub-title="error">
      <template #extra>
        <a-button type="primary" @click="router.replace('/login')">返回登录</a-button>
      </template>
    </a-result>
  </div>
</template>

<style scoped>
.callback-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
