<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import {
  ThunderboltOutlined,
  UserOutlined,
  LogoutOutlined,
  CrownOutlined,
  DownOutlined,
  UnorderedListOutlined,
  GithubOutlined,
  DeleteOutlined,
  ReloadOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  LoadingOutlined,
  ClockCircleOutlined,
  BarChartOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { useReviewTaskStore, type ReviewTask } from '@/stores/reviewTasks'
import StatsOverviewModal from '@/components/StatsOverviewModal.vue'

const userStore = useUserStore()
const taskStore = useReviewTaskStore()
const router = useRouter()

const initials = computed(() => userStore.username?.slice(0, 1).toUpperCase() || 'U')

const drawerOpen = ref(false)
const newPrUrl = ref('')
const drawerErrorMsg = ref('')
const statsOpen = ref(false)

const PR_URL_RE = /^https:\/\/github\.com\/[^/]+\/[^/]+\/pull\/\d+/i

onMounted(() => {
  taskStore.resumeAll()
})

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

function openDrawer() {
  drawerOpen.value = true
  if (!taskStore.historyLoaded) {
    void taskStore.loadHistory()
  }
}

function refreshHistory() {
  void taskStore.loadHistory()
}

function closeDrawer() {
  drawerOpen.value = false
  drawerErrorMsg.value = ''
}

async function submitFromDrawer() {
  drawerErrorMsg.value = ''
  const url = newPrUrl.value.trim()
  if (!url) {
    drawerErrorMsg.value = '请输入 PR 链接'
    return
  }
  if (!PR_URL_RE.test(url)) {
    drawerErrorMsg.value = '链接格式不正确'
    return
  }
  void taskStore.submit(url)
  newPrUrl.value = ''
  message.success('已加入分析队列')
}

function openDetail(task: ReviewTask) {
  if (!task.id) return
  router.push({ name: 'review-detail', params: { id: task.id } })
  closeDrawer()
}

function removeTask(task: ReviewTask, e?: Event) {
  e?.stopPropagation()
  const isRemote = !!task.id && task.status !== 'submitting'
  Modal.confirm({
    title: isRemote ? '删除该评审记录？' : '从列表中移除该任务？',
    content: isRemote
      ? '将同时从服务器删除该记录，删除后无法恢复。'
      : '该任务尚未提交成功，仅会从本地列表移除。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      if (isRemote) {
        try {
          await taskStore.deleteRemote(task.localId)
          message.success('已删除')
        } catch {
          // 拦截器已提示错误，保留本地条目供重试
        }
      } else {
        taskStore.remove(task.localId)
      }
    },
  })
}

function clearFinished() {
  if (!taskStore.finished.length) return
  Modal.confirm({
    title: '删除全部已完成记录？',
    content: `将从服务器删除 ${taskStore.finished.length} 条已完成/失败的评审记录，删除后无法恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      const targets = [...taskStore.finished]
      let removed = 0
      for (const t of targets) {
        try {
          await taskStore.deleteRemote(t.localId)
          removed++
        } catch {
          // 单条失败继续后续，剩余条目保留供重试
        }
      }
      if (removed === targets.length) {
        message.success(`已删除 ${removed} 条记录`)
      } else if (removed > 0) {
        message.warning(`已删除 ${removed} 条，${targets.length - removed} 条删除失败`)
      } else {
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

function statusOf(task: ReviewTask) {
  switch (task.status) {
    case 'submitting':
      return { text: '提交中', color: 'blue', icon: LoadingOutlined, spin: true }
    case 'pending':
      return { text: '排队中', color: 'blue', icon: ClockCircleOutlined, spin: false }
    case 'processing':
      return { text: '分析中', color: 'processing', icon: LoadingOutlined, spin: true }
    case 'completed':
      return { text: '已完成', color: 'success', icon: CheckCircleFilled, spin: false }
    case 'error':
      return { text: '失败', color: 'error', icon: CloseCircleFilled, spin: false }
    default:
      return { text: task.status, color: 'default', icon: ClockCircleOutlined, spin: false }
  }
}

function shortUrl(url: string) {
  return url.replace(/^https:\/\/github\.com\//, '').replace(/\/pull\//, ' #')
}

function relativeTime(ts: number) {
  if (!ts) return ''
  const diff = Date.now() - ts
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return `${Math.floor(diff / 86_400_000)} 天前`
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
          <a-button type="text" class="task-btn" @click="openDrawer">
            <a-badge
              :count="taskStore.inProgressCount"
              :number-style="{ backgroundColor: '#1677ff' }"
              :offset="[2, -2]"
            >
              <UnorderedListOutlined class="task-icon" />
            </a-badge>
            <span class="task-btn-text">任务</span>
          </a-button>

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
                <a-menu-item key="stats" @click="statsOpen = true">
                  <BarChartOutlined />
                  我的统计
                </a-menu-item>
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

    <a-drawer
      v-model:open="drawerOpen"
      title="分析任务中心"
      placement="right"
      :width="460"
      :body-style="{ padding: 0 }"
    >
      <template #extra>
        <a-space :size="4">
          <a-button
            type="text"
            size="small"
            :loading="taskStore.historyLoading"
            @click="refreshHistory"
          >
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
          <a-button
            type="text"
            size="small"
            danger
            :disabled="!taskStore.finished.length"
            @click="clearFinished"
          >
            <template #icon><DeleteOutlined /></template>
            删除已完成
          </a-button>
        </a-space>
      </template>

      <div class="drawer-form">
        <a-form layout="vertical" @submit.prevent="submitFromDrawer">
          <a-form-item
            label="提交新分析"
            :validate-status="drawerErrorMsg ? 'error' : ''"
            :help="drawerErrorMsg || undefined"
          >
            <a-input-group compact style="display: flex">
              <a-input
                v-model:value="newPrUrl"
                placeholder="https://github.com/owner/repo/pull/123"
                allow-clear
                style="flex: 1"
              >
                <template #prefix>
                  <GithubOutlined style="color: rgba(0,0,0,0.45)" />
                </template>
              </a-input>
              <a-button type="primary" html-type="submit">
                提交
              </a-button>
            </a-input-group>
          </a-form-item>
        </a-form>
      </div>

      <a-divider class="drawer-divider" />

      <div class="drawer-section">
        <div class="drawer-section-head">
          <span class="drawer-section-title">进行中</span>
          <a-tag color="blue">{{ taskStore.inProgressCount }}</a-tag>
        </div>
        <a-empty
          v-if="!taskStore.inProgress.length"
          :image="undefined"
          description="暂无进行中的任务"
          class="drawer-empty"
        />
        <ul v-else class="task-list">
          <li
            v-for="t in taskStore.inProgress"
            :key="t.localId"
            class="task-item"
            :class="{ clickable: !!t.id }"
            @click="t.id ? openDetail(t) : null"
          >
            <div class="task-row">
              <component
                :is="statusOf(t).icon"
                :spin="statusOf(t).spin"
                class="task-status-icon"
                :class="`status-${t.status}`"
              />
              <div class="task-main">
                <div class="task-title">
                  {{ t.prTitle || shortUrl(t.prUrl) || '未命名任务' }}
                </div>
                <div class="task-meta">
                  <a-tag :color="statusOf(t).color" class="task-status-tag">
                    {{ statusOf(t).text }}
                  </a-tag>
                  <span class="task-time">{{ relativeTime(t.createdAt) }}</span>
                </div>
              </div>
              <a-button
                type="text"
                size="small"
                @click="(e) => removeTask(t, e)"
              >
                <template #icon><DeleteOutlined /></template>
              </a-button>
            </div>
          </li>
        </ul>
      </div>

      <a-divider class="drawer-divider" />

      <div class="drawer-section">
        <div class="drawer-section-head">
          <span class="drawer-section-title">历史记录</span>
          <a-tag>{{ taskStore.finished.length }}</a-tag>
        </div>
        <a-empty
          v-if="!taskStore.finished.length"
          :image="undefined"
          description="暂无历史记录"
          class="drawer-empty"
        />
        <ul v-else class="task-list">
          <li
            v-for="t in taskStore.finished"
            :key="t.localId"
            class="task-item"
            :class="{ clickable: !!t.id }"
            @click="t.id ? openDetail(t) : null"
          >
            <div class="task-row">
              <component
                :is="statusOf(t).icon"
                class="task-status-icon"
                :class="`status-${t.status}`"
              />
              <div class="task-main">
                <div class="task-title">
                  {{ t.prTitle || shortUrl(t.prUrl) || '未命名任务' }}
                </div>
                <div class="task-meta">
                  <a-tag :color="statusOf(t).color" class="task-status-tag">
                    {{ statusOf(t).text }}
                  </a-tag>
                  <span v-if="t.status === 'completed'" class="task-counts">
                    风险 {{ t.riskCount }} · 建议 {{ t.suggestionCount }}
                  </span>
                  <span class="task-time">
                    {{ t.finishedAt ? relativeTime(t.finishedAt) : relativeTime(t.createdAt) }}
                  </span>
                </div>
              </div>
              <a-button
                type="text"
                size="small"
                @click="(e) => removeTask(t, e)"
              >
                <template #icon><DeleteOutlined /></template>
              </a-button>
            </div>
          </li>
        </ul>
      </div>
    </a-drawer>

    <StatsOverviewModal v-model:open="statsOpen" />
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
  gap: 4px;
}
.task-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 12px;
}
.task-icon {
  font-size: 18px;
  color: rgba(0, 0, 0, 0.7);
}
.task-btn-text {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
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

/* drawer */
.drawer-form {
  padding: 16px 24px 0;
}
.drawer-divider {
  margin: 12px 0;
}
.drawer-section {
  padding: 0 24px 8px;
}
.drawer-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.drawer-section-title {
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
  font-size: 14px;
}
.drawer-empty {
  padding: 16px 0;
}
.task-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.task-item {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
  padding: 10px 12px;
  transition: border-color .15s, box-shadow .15s;
}
.task-item.clickable {
  cursor: pointer;
}
.task-item.clickable:hover {
  border-color: #1677ff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.08);
}
.task-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.task-status-icon {
  font-size: 18px;
  margin-top: 2px;
  flex-shrink: 0;
}
.status-completed { color: #52c41a; }
.status-error { color: #ff4d4f; }
.status-processing,
.status-submitting { color: #1677ff; }
.status-pending { color: #faad14; }
.task-main {
  flex: 1;
  min-width: 0;
}
.task-title {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.85);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.task-meta {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.task-status-tag {
  margin: 0;
}
.task-counts,
.task-time {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
</style>
