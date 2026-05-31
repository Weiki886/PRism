<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, type FormProps } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  UserOutlined,
  MailOutlined,
  LockOutlined,
  GithubOutlined,
  CrownOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { updatePassword, updateProfile } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

const profileForm = reactive({ email: '' })
const profileFormRef = ref()
const profileSubmitting = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const passwordFormRef = ref()
const passwordSubmitting = ref(false)

const profileRules: FormProps['rules'] = {
  email: [
    { required: true, message: '邮箱不能为空', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}

const passwordRules: FormProps['rules'] = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 100, message: '新密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: async (_rule: unknown, value: string) => {
        if (value && value !== passwordForm.newPassword) {
          throw new Error('两次输入的新密码不一致')
        }
      },
      trigger: 'blur',
    },
  ],
}

async function onProfileFinish() {
  profileSubmitting.value = true
  try {
    await updateProfile({ email: profileForm.email })
    message.success('邮箱已更新')
    profileForm.email = ''
    profileFormRef.value?.resetFields()
  } catch {
    // 拦截器已提示（如 409 邮箱已被占用）
  } finally {
    profileSubmitting.value = false
  }
}

async function onPasswordFinish() {
  passwordSubmitting.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    message.success('密码已更新')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordFormRef.value?.resetFields()
  } catch {
    // 拦截器已提示（如 400 密码错误）
  } finally {
    passwordSubmitting.value = false
  }
}

function goBack() {
  router.push({ name: 'home' })
}

const initials = userStore.username?.slice(0, 1).toUpperCase() || 'U'
</script>

<template>
  <div class="account">
    <div class="container">
      <a-page-header
        class="page-header"
        title="账户设置"
        sub-title="管理你的账号信息与登录凭据"
        @back="goBack"
      >
        <template #backIcon>
          <ArrowLeftOutlined />
        </template>
      </a-page-header>

      <a-card :bordered="false" class="section">
        <template #title>
          <UserOutlined />
          <span class="section-title-text">账号信息</span>
        </template>
        <div class="profile-row">
          <a-avatar
            v-if="userStore.avatarUrl"
            :src="userStore.avatarUrl"
            :size="56"
            class="profile-avatar"
          />
          <a-avatar
            v-else
            :size="56"
            class="profile-avatar"
            style="background-color: #1677ff"
          >
            {{ initials }}
          </a-avatar>
          <div class="profile-meta">
            <div class="profile-name">{{ userStore.username }}</div>
            <div class="profile-tags">
              <a-tag v-if="userStore.isAdmin" color="gold">
                <CrownOutlined /> ADMIN
              </a-tag>
              <a-tag v-if="userStore.isGithubLinked" color="default" class="github-tag">
                <GithubOutlined /> {{ userStore.githubLogin }}
              </a-tag>
              <a-tag v-else color="default">本地账号</a-tag>
            </div>
          </div>
        </div>
      </a-card>

      <a-card :bordered="false" class="section">
        <template #title>
          <MailOutlined />
          <span class="section-title-text">修改邮箱</span>
        </template>
        <a-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          layout="vertical"
          class="settings-form"
          @finish="onProfileFinish"
        >
          <a-form-item label="新邮箱" name="email">
            <a-input
              v-model:value="profileForm.email"
              placeholder="example@domain.com"
              allow-clear
              autocomplete="email"
            >
              <template #prefix>
                <MailOutlined style="color: rgba(0,0,0,0.45)" />
              </template>
            </a-input>
          </a-form-item>
          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              :loading="profileSubmitting"
            >
              保存邮箱
            </a-button>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card :bordered="false" class="section">
        <template #title>
          <LockOutlined />
          <span class="section-title-text">修改密码</span>
        </template>
        <a-alert
          v-if="userStore.isGithubLinked && !userStore.isAdmin"
          type="info"
          show-icon
          message="GitHub 登录用户：如果你从未为账号设置过本地密码，修改密码会因「当前密码错误」失败。"
          class="settings-hint"
        />
        <a-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          layout="vertical"
          class="settings-form"
          @finish="onPasswordFinish"
        >
          <a-form-item label="当前密码" name="oldPassword">
            <a-input-password
              v-model:value="passwordForm.oldPassword"
              placeholder="请输入当前密码"
              autocomplete="current-password"
            >
              <template #prefix>
                <LockOutlined style="color: rgba(0,0,0,0.45)" />
              </template>
            </a-input-password>
          </a-form-item>
          <a-form-item label="新密码" name="newPassword">
            <a-input-password
              v-model:value="passwordForm.newPassword"
              placeholder="至少 6 位"
              autocomplete="new-password"
            >
              <template #prefix>
                <LockOutlined style="color: rgba(0,0,0,0.45)" />
              </template>
            </a-input-password>
          </a-form-item>
          <a-form-item label="确认新密码" name="confirmPassword">
            <a-input-password
              v-model:value="passwordForm.confirmPassword"
              placeholder="再次输入新密码"
              autocomplete="new-password"
            >
              <template #prefix>
                <LockOutlined style="color: rgba(0,0,0,0.45)" />
              </template>
            </a-input-password>
          </a-form-item>
          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              :loading="passwordSubmitting"
            >
              更新密码
            </a-button>
          </a-form-item>
        </a-form>
      </a-card>
    </div>
  </div>
</template>

<style scoped>
.account {
  min-height: 100vh;
  background: #f0f2f5;
  padding: 24px 16px 64px;
}
.container {
  max-width: 720px;
  margin: 0 auto;
}
.page-header {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}
.section {
  margin-top: 16px;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}
.section-title-text {
  margin-left: 8px;
  font-weight: 600;
}
.profile-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.profile-avatar {
  flex-shrink: 0;
}
.profile-meta {
  flex: 1;
  min-width: 0;
}
.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 6px;
}
.profile-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.github-tag {
  border: 1px solid #d9d9d9;
  background: #fafafa;
  color: rgba(0, 0, 0, 0.65);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.settings-form {
  max-width: 420px;
}
.settings-hint {
  margin-bottom: 16px;
}
</style>
