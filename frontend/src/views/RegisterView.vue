<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, type FormProps } from 'ant-design-vue'
import { UserOutlined, LockOutlined, MailOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { register as apiRegister } from '@/api/auth'

const router = useRouter()

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const rules: FormProps['rules'] = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50 位', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    {
      validator: (_: unknown, value: string) => {
        if (!value) return Promise.reject('请再次输入密码')
        if (value !== form.password) return Promise.reject('两次输入密码不一致')
        return Promise.resolve()
      },
      trigger: 'blur',
    },
  ],
}

const loading = ref(false)
const formRef = ref()

async function onSubmit() {
  loading.value = true
  try {
    await apiRegister({
      username: form.username,
      email: form.email,
      password: form.password,
    })
    message.success('注册成功，请登录')
    router.push({ name: 'login' })
  } catch {
    /* 错误已由拦截器统一弹 message */
  } finally {
    loading.value = false
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
      <a-typography-title :level="3" class="title">注册</a-typography-title>
      <a-typography-paragraph type="secondary">创建账号开始使用 PRism</a-typography-paragraph>

      <a-form
        ref="formRef"
        :model="form"
        :rules="rules"
        layout="vertical"
        @finish="onSubmit"
      >
        <a-form-item label="用户名" name="username">
          <a-input v-model:value="form.username" size="large" placeholder="3-50 位字符" allow-clear>
            <template #prefix>
              <UserOutlined style="color: rgba(0,0,0,0.45)" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item label="邮箱" name="email">
          <a-input v-model:value="form.email" size="large" placeholder="example@domain.com" allow-clear>
            <template #prefix>
              <MailOutlined style="color: rgba(0,0,0,0.45)" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item label="密码" name="password">
          <a-input-password v-model:value="form.password" size="large" placeholder="至少 6 位">
            <template #prefix>
              <LockOutlined style="color: rgba(0,0,0,0.45)" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item label="确认密码" name="confirmPassword">
          <a-input-password v-model:value="form.confirmPassword" size="large" placeholder="再次输入密码">
            <template #prefix>
              <LockOutlined style="color: rgba(0,0,0,0.45)" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" :loading="loading" block>
            注册
          </a-button>
        </a-form-item>
      </a-form>

      <div class="alt">
        已有账号？
        <router-link to="/login">立即登录</router-link>
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
  max-width: 460px;
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
