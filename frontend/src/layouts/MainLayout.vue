<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import {
  ThunderboltOutlined,
  UserOutlined,
  LogoutOutlined,
  CrownOutlined,
  DownOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

const initials = computed(() => userStore.username?.slice(0, 1).toUpperCase() || 'U')

function handleLogout() {
  Modal.confirm({
    title: '确认退出登录？',
    content: '退出后需要重新登录',
    okText: '退出',
    cancelText: '取消',
    onOk: () => {
      userStore.logout()
      message.success('已退出登录')
      router.replace({ name: 'login' })
    },
  })
}
</script>

<template>
  <a-layout class="layout">
    <a-layout-header class="header">
      <div class="header-inner">
        <router-link to="/" class="brand">
          <div class="logo">
            <ThunderboltOutlined />
          </div>
          <div class="brand-name">PRism</div>
          <a-tag color="blue" class="brand-tag">Enterprise</a-tag>
        </router-link>

        <div class="actions">
          <a-dropdown placement="bottomRight">
            <div class="user">
              <a-avatar style="background-color: #1677ff">{{ initials }}</a-avatar>
              <span class="username">{{ userStore.username }}</span>
              <a-tag v-if="userStore.isAdmin" color="gold" class="role-tag">
                <CrownOutlined /> ADMIN
              </a-tag>
              <DownOutlined style="font-size: 12px; color: rgba(0,0,0,0.45)" />
            </div>
            <template #overlay>
              <a-menu>
                <a-menu-item key="user" disabled>
                  <UserOutlined />
                  {{ userStore.username }}
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="handleLogout">
                  <LogoutOutlined />
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </div>
    </a-layout-header>

    <a-layout-content class="content">
      <router-view />
    </a-layout-content>

    <a-layout-footer class="footer">
      PRism · Enterprise Edition · 七牛云 × XEngineer 暑期实训营
    </a-layout-footer>
  </a-layout>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  background: #f0f2f5;
}
.header {
  background: #fff;
  padding: 0;
  border-bottom: 1px solid #f0f0f0;
  height: 56px;
  line-height: 56px;
  position: sticky;
  top: 0;
  z-index: 10;
}
.header-inner {
  max-width: 1280px;
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
}
.logo {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #1677ff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}
.brand-name {
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}
.brand-tag {
  margin-left: 4px;
  margin-right: 0;
}
.actions {
  display: flex;
  align-items: center;
}
.user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  cursor: pointer;
  height: 56px;
  border-radius: 4px;
  transition: background .15s;
}
.user:hover {
  background: rgba(0, 0, 0, 0.025);
}
.username {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
}
.role-tag {
  margin: 0;
}
.content {
  min-height: calc(100vh - 56px - 48px);
}
.footer {
  text-align: center;
  background: transparent;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
  padding: 12px 24px;
  height: 48px;
}
</style>
