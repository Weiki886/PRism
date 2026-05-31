<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Modal, message } from 'ant-design-vue'
import {
  UserOutlined,
  CrownOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { listUsers, updateUserRole, deleteUser, type AdminUser } from '@/api/admin'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const users = ref<AdminUser[]>([])
const loading = ref(false)
const searchText = ref('')

const filteredUsers = computed(() => {
  const kw = searchText.value.trim().toLowerCase()
  if (!kw) return users.value
  return users.value.filter(
    (u) =>
      u.username.toLowerCase().includes(kw) ||
      u.email.toLowerCase().includes(kw) ||
      u.role.toLowerCase().includes(kw),
  )
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: '角色', dataIndex: 'role', key: 'role', width: 120 },
  { title: '注册时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const },
]

async function fetchUsers(clearSearch = false) {
  if (clearSearch) searchText.value = ''
  loading.value = true
  try {
    users.value = await listUsers()
  } catch {
    // 全局拦截器已处理
  } finally {
    loading.value = false
  }
}

function handleRoleChange(user: AdminUser) {
  const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  const actionText = newRole === 'ADMIN' ? '提升为管理员' : '降级为普通用户'

  Modal.confirm({
    title: `确认${actionText}？`,
    content: `将用户 "${user.username}" ${actionText}`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await updateUserRole(user.id, newRole)
        message.success(`已将 ${user.username} ${actionText}`)
        await fetchUsers()
      } catch {
        // 全局拦截器已处理
      }
    },
  })
}

async function handleDelete(user: AdminUser) {
  if (user.username === userStore.username) {
    message.warning('不能删除自己的账号')
    return
  }
  try {
    await deleteUser(user.id)
    message.success(`已删除用户 ${user.username}`)
    await fetchUsers()
  } catch {
    // 全局拦截器已处理
  }
}

function formatTime(dateStr: string) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div class="admin-users">
    <a-card :bordered="false" class="card">
      <div class="header">
        <div class="header-left">
          <a-typography-title :level="4" class="page-title">
            <CrownOutlined class="title-icon" />
            用户管理
          </a-typography-title>
          <a-typography-text type="secondary" class="page-desc">
            管理平台用户，修改角色或删除账号
          </a-typography-text>
        </div>
        <div class="header-right">
          <a-input
            v-model:value="searchText"
            placeholder="搜索用户名 / 邮箱"
            allow-clear
            style="width: 220px"
          >
            <template #prefix>
              <SearchOutlined style="color: rgba(0,0,0,0.45)" />
            </template>
          </a-input>
          <a-button @click="fetchUsers(true)" :loading="loading">
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
        </div>
      </div>

      <a-table
        :columns="columns"
        :data-source="filteredUsers"
        :loading="loading"
        :pagination="{ pageSize: 15, showSizeChanger: true, showTotal: (t: number) => `共 ${t} 位用户` }"
        row-key="id"
        size="middle"
        :scroll="{ x: 800 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'username'">
            <div class="user-cell">
              <a-avatar :size="28" class="user-avatar">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <span class="user-name">{{ record.username }}</span>
              <a-tag v-if="record.username === userStore.username" color="blue" class="self-tag">
                我
              </a-tag>
            </div>
          </template>

          <template v-if="column.key === 'role'">
            <a-tag :color="record.role === 'ADMIN' ? 'gold' : 'default'">
              <CrownOutlined v-if="record.role === 'ADMIN'" />
              {{ record.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>

          <template v-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>

          <template v-if="column.key === 'action'">
            <a-space>
              <a-button
                size="small"
                type="primary"
                ghost
                @click="handleRoleChange(record)"
                :disabled="record.username === userStore.username"
              >
                {{ record.role === 'ADMIN' ? '降为用户' : '设为管理员' }}
              </a-button>
              <a-popconfirm
                title="确认删除该用户？"
                description="删除后该用户所有数据将被清除，不可恢复。"
                ok-text="删除"
                cancel-text="取消"
                :ok-button-props="{ danger: true }"
                @confirm="handleDelete(record)"
                :disabled="record.username === userStore.username"
              >
                <a-button
                  size="small"
                  danger
                  :disabled="record.username === userStore.username"
                >
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.admin-users {
  padding: 24px;
  background: #f0f2f5;
  min-height: calc(100vh - 56px);
}
.card {
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03), 0 4px 16px rgba(0, 0, 0, 0.04);
}
.header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}
.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.page-title {
  margin: 0 !important;
  display: flex;
  align-items: center;
  gap: 8px;
}
.title-icon {
  color: #faad14;
}
.page-desc {
  font-size: 13px;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-avatar {
  background: #1677ff;
  flex-shrink: 0;
}
.user-name {
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
}
.self-tag {
  margin-left: 4px;
}
</style>
