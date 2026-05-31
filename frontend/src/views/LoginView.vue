<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message, type FormProps } from 'ant-design-vue'
import { UserOutlined, LockOutlined, ThunderboltOutlined, GithubOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { getGithubAuthorizeUrl } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = reactive({
  username: '',
  password: '',
})

const rules: FormProps['rules'] = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const loading = ref(false)
const githubLoading = ref(false)
const formRef = ref()

async function onSubmit() {
  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    message.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch {
    /* 错误已由拦截器统一弹 message */
  } finally {
    loading.value = false
  }
}

async function onGithubLogin() {
  githubLoading.value = true
  try {
    const { authorizeUrl } = await getGithubAuthorizeUrl()
    window.location.href = authorizeUrl
  } catch {
    message.error('获取 GitHub 授权链接失败')
  } finally {
    githubLoading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="brand">
      <div class="logo">
        <ThunderboltOutlined />
      </div>
      <div class="brand-text">
        <div class="brand-name">PRism</div>
        <div class="brand-desc">AI Code Review Platform</div>
      </div>
    </div>

    <a-card :bordered="false" class="auth-card">
      <a-typography-title :level="3" class="title">登录</a-typography-title>
      <a-typography-paragraph type="secondary">使用账号登录以开始 PR 审查</a-typography-paragraph>

      <a-form
        ref="formRef"
        :model="form"
        :rules="rules"
        layout="vertical"
        @finish="onSubmit"
      >
        <a-form-item label="用户名" name="username">
          <a-input
            v-model:value="form.username"
            size="large"
            placeholder="请输入用户名"
            autocomplete="username"
            allow-clear
          >
            <template #prefix>
              <UserOutlined style="color: rgba(0,0,0,0.45)" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item label="密码" name="password">
          <a-input-password
            v-model:value="form.password"
            size="large"
            placeholder="请输入密码"
            autocomplete="current-password"
          >
            <template #prefix>
              <LockOutlined style="color: rgba(0,0,0,0.45)" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" :loading="loading" block>
            登录
          </a-button>
        </a-form-item>
      </a-form>

      <a-divider>或</a-divider>

      <a-button size="large" block :loading="githubLoading" @click="onGithubLogin">
        <template #icon><GithubOutlined /></template>
        使用 GitHub 登录
      </a-button>

      <div class="alt" style="margin-top: 16px">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  background: #f0f2f5;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}
.logo {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: #1677ff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}
.brand-name {
  font-size: 20px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  line-height: 1.2;
}
.brand-desc {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  letter-spacing: 0.5px;
}
.auth-card {
  width: 100%;
  max-width: 420px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03), 0 4px 16px rgba(0, 0, 0, 0.04);
  border-radius: 8px;
}
.title {
  margin-bottom: 4px !important;
}
.alt {
  text-align: center;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}
.alt a {
  color: #1677ff;
  margin-left: 4px;
}
</style>
