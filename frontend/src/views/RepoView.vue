<script setup lang="ts">
import { inject, onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import {
  GithubOutlined,
  StarOutlined,
  ForkOutlined,
  IssuesCloseOutlined,
  SearchOutlined,
  ExclamationCircleOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  HistoryOutlined,
  CloseOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import {
  clearRepoHistory,
  deleteRepoHistory,
  getRepoHistory,
  getRepoInfo,
  getRepoPulls,
  type RepoBrowsingRecord,
  type RepoInfo,
  type RepoPullRequest,
} from '@/api/repo'
import { DuplicateError, retryReview } from '@/api/review'
import { useReviewTaskStore } from '@/stores/reviewTasks'

const openDrawer = inject<() => void>('openDrawer')
const router = useRouter()
const taskStore = useReviewTaskStore()

const DUPLICATE_STATUS_LABEL: Record<string, string> = {
  completed: '已完成', processing: '分析中', pending: '排队中', error: '上次失败',
}

function showDuplicateModal(err: DuplicateError) {
  const label = DUPLICATE_STATUS_LABEL[err.info.status] ?? err.info.status
  const canRetry = err.info.status === 'error'
  Modal.confirm({
    title: '该 PR 已有分析记录',
    content: `「${err.info.prTitle}」已提交过分析（${label}）。`,
    okText: '查看记录',
    cancelText: canRetry ? '重新分析' : '关闭',
    onOk: () => {
      router.push({ name: 'review-detail', params: { id: err.info.existingReviewId } })
    },
    onCancel: canRetry ? async () => {
      await retryReview(err.info.existingReviewId)
      await taskStore.refreshOne(err.info.existingReviewId)
      message.success('已重新加入分析队列')
      openDrawer?.()
    } : undefined,
  })
}

const repoUrl = ref('')
const repoInfo = ref<RepoInfo | null>(null)
const pulls = ref<RepoPullRequest[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const state = ref<'open' | 'closed' | 'all'>('open')
const infoLoading = ref(false)
const pullsLoading = ref(false)
const infoError = ref('')
const pullsError = ref('')
const analyzingIds = ref(new Set<number>())

const REPO_URL_RE = /^https:\/\/github\.com\/[^/]+\/[^/]+\/?$/i

const urlValid = computed(() => REPO_URL_RE.test(repoUrl.value.trim()))

const historyList = ref<RepoBrowsingRecord[]>([])
const historyLoading = ref(false)
const historyDeletingId = ref<number | null>(null)

const stateOptions = [
  { label: 'Open', value: 'open' },
  { label: 'Closed', value: 'closed' },
  { label: '全部', value: 'all' },
]

async function loadHistory() {
  historyLoading.value = true
  try {
    historyList.value = await getRepoHistory(20)
  } catch {
    // 历史列表加载失败不阻塞主功能
  } finally {
    historyLoading.value = false
  }
}

onMounted(() => {
  void loadHistory()
  const urlFromQuery = router.currentRoute.value.query.url as string | undefined
  if (urlFromQuery) {
    const decoded = decodeURIComponent(urlFromQuery)
    repoUrl.value = decoded
    void fetchRepoInfo().then(() => {
      // 清除 query 参数，保持 URL 干净
      router.replace({ name: 'repo-browser' })
    })
  }
})

async function fetchRepoInfo() {
  const url = repoUrl.value.trim()
  if (!url) return
  if (!urlValid.value) {
    infoError.value = '链接格式不正确，示例：https://github.com/owner/repo'
    return
  }
  infoLoading.value = true
  pullsLoading.value = true
  infoError.value = ''
  pullsError.value = ''
  repoInfo.value = null
  pulls.value = []
  total.value = 0
  page.value = 1
  try {
    repoInfo.value = await getRepoInfo(url)
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } }; message?: string }
    infoError.value = e?.response?.data?.message ?? e?.message ?? '获取仓库信息失败'
    infoLoading.value = false
    pullsLoading.value = false
    return
  } finally {
    infoLoading.value = false
  }
  await fetchPulls()
  // 命中一次有效仓库后刷新最近浏览（后端已自动登记）
  void loadHistory()
}

async function fetchPulls(p = page.value) {
  pullsLoading.value = true
  pullsError.value = ''
  try {
    const result = await getRepoPulls(repoUrl.value.trim(), p, pageSize.value, state.value)
    pulls.value = result.records
    total.value = result.total
    page.value = result.page
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } }; message?: string }
    pullsError.value = e?.response?.data?.message ?? e?.message ?? '获取 PR 列表失败'
  } finally {
    pullsLoading.value = false
  }
}

async function onStateChange(s: 'open' | 'closed' | 'all') {
  state.value = s
  page.value = 1
  await fetchPulls(1)
}

async function onPageChange(p: number) {
  await fetchPulls(p)
}

async function analyzePr(pr: RepoPullRequest) {
  if (analyzingIds.value.has(pr.number)) return
  analyzingIds.value.add(pr.number)
  try {
    await taskStore.submit(pr.htmlUrl)
    message.success(`已将「${pr.title}」加入分析队列`)
    openDrawer?.()
  } catch (err) {
    if (err instanceof DuplicateError) {
      showDuplicateModal(err)
    }
  } finally {
    analyzingIds.value.delete(pr.number)
  }
}

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const diff = Date.now() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  if (diff < 2_592_000_000) return `${Math.floor(diff / 86_400_000)} 天前`
  return d.toLocaleDateString('zh-CN')
}

async function openHistoryRepo(record: RepoBrowsingRecord) {
  repoUrl.value = record.repoUrl
  await fetchRepoInfo()
}

async function removeHistory(record: RepoBrowsingRecord, e?: Event) {
  e?.stopPropagation()
  if (historyDeletingId.value === record.id) return
  historyDeletingId.value = record.id
  try {
    await deleteRepoHistory(record.id)
    historyList.value = historyList.value.filter((r) => r.id !== record.id)
  } catch {
    // 拦截器已提示
  } finally {
    historyDeletingId.value = null
  }
}

function confirmClearHistory() {
  if (!historyList.value.length) return
  Modal.confirm({
    title: '清空全部浏览记录？',
    content: `将清空当前 ${historyList.value.length} 条最近浏览的仓库记录，清空后无法恢复。`,
    okText: '清空',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await clearRepoHistory()
        historyList.value = []
        message.success('已清空浏览记录')
      } catch {
        // 拦截器已提示
      }
    },
  })
}
</script>

<template>
  <div class="repo-page">
    <div class="container">
      <div class="page-title">浏览仓库 PR</div>
      <div class="page-desc">输入 GitHub 仓库链接，浏览 PR 列表并一键发起 AI 分析</div>

      <!-- 输入栏 -->
      <div class="search-bar">
        <a-input
          v-model:value="repoUrl"
          placeholder="https://github.com/owner/repo"
          size="large"
          allow-clear
          class="repo-input"
          @press-enter="fetchRepoInfo"
        >
          <template #prefix>
            <GithubOutlined style="color: rgba(0,0,0,0.35)" />
          </template>
        </a-input>
        <a-button
          type="primary"
          size="large"
          :loading="infoLoading || pullsLoading"
          :disabled="!repoUrl.trim()"
          @click="fetchRepoInfo"
        >
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
      </div>

      <a-alert
        v-if="infoError"
        type="error"
        show-icon
        :message="infoError"
        class="error-alert"
      />

      <!-- 最近浏览过的仓库 -->
      <a-card
        v-if="historyList.length || historyLoading"
        :bordered="false"
        class="history-card"
        size="small"
      >
        <template #title>
          <span class="history-title">
            <HistoryOutlined />
            <span class="history-title-text">最近浏览</span>
            <a-tag color="blue">{{ historyList.length }}</a-tag>
          </span>
        </template>
        <template #extra>
          <a-button
            type="link"
            size="small"
            danger
            :disabled="!historyList.length"
            @click="confirmClearHistory"
          >
            <template #icon><DeleteOutlined /></template>
            清空
          </a-button>
        </template>
        <a-spin :spinning="historyLoading">
          <ul v-if="historyList.length" class="history-list">
            <li
              v-for="record in historyList"
              :key="record.id"
              class="history-item"
              @click="openHistoryRepo(record)"
            >
              <a-avatar
                v-if="record.ownerAvatarUrl"
                :src="record.ownerAvatarUrl"
                :size="28"
                class="history-avatar"
              />
              <div class="history-info">
                <div class="history-name">
                  <span class="history-fullname">{{ record.fullName }}</span>
                  <a-tag v-if="record.isPrivate" color="orange" class="history-private">Private</a-tag>
                  <span v-if="record.language" class="history-lang">{{ record.language }}</span>
                </div>
                <div class="history-sub">
                  <span v-if="record.description" class="history-desc">{{ record.description }}</span>
                  <span class="history-time">{{ formatTime(record.lastVisitedAt) }}</span>
                </div>
              </div>
              <a-button
                type="text"
                size="small"
                :loading="historyDeletingId === record.id"
                class="history-remove-btn"
                @click="(e) => removeHistory(record, e)"
              >
                <template #icon><CloseOutlined /></template>
              </a-button>
            </li>
          </ul>
        </a-spin>
      </a-card>

      <!-- 仓库信息卡 -->
      <a-card v-if="repoInfo" :bordered="false" class="repo-card">
        <div class="repo-header">
          <a-avatar :src="repoInfo.ownerAvatarUrl" :size="48" class="owner-avatar" />
          <div class="repo-meta">
            <div class="repo-name">
              <a :href="repoInfo.htmlUrl" target="_blank" rel="noopener">
                {{ repoInfo.fullName }}
              </a>
              <a-tag v-if="repoInfo.isPrivate" color="orange" class="private-tag">Private</a-tag>
              <a-tag v-else color="default" class="private-tag">Public</a-tag>
            </div>
            <div v-if="repoInfo.description" class="repo-desc">{{ repoInfo.description }}</div>
            <div class="repo-stats">
              <span class="stat-item">
                <StarOutlined /> {{ repoInfo.starCount.toLocaleString() }}
              </span>
              <span class="stat-item">
                <ForkOutlined /> {{ repoInfo.forkCount.toLocaleString() }}
              </span>
              <span v-if="repoInfo.language" class="stat-item lang">
                {{ repoInfo.language }}
              </span>
              <span class="stat-item">
                <IssuesCloseOutlined /> {{ repoInfo.openIssuesCount }} open issues
              </span>
            </div>
            <div v-if="repoInfo.topics?.length" class="repo-topics">
              <a-tag v-for="t in repoInfo.topics" :key="t" class="topic-tag">{{ t }}</a-tag>
            </div>
          </div>
        </div>
      </a-card>

      <!-- PR 列表 -->
      <a-card v-if="repoInfo" :bordered="false" class="pulls-card">
        <template #title>
          <div class="pulls-title-row">
            <span>Pull Requests</span>
            <a-radio-group
              :value="state"
              size="small"
              button-style="solid"
              @change="(e: Event) => onStateChange((e.target as HTMLInputElement).value as 'open' | 'closed' | 'all')"
            >
              <a-radio-button v-for="opt in stateOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </a-radio-button>
            </a-radio-group>
          </div>
        </template>

        <a-alert
          v-if="pullsError"
          type="error"
          show-icon
          :message="pullsError"
          style="margin-bottom: 12px"
        />

        <a-spin :spinning="pullsLoading">
          <a-empty
            v-if="!pulls.length && !pullsLoading"
            :image="undefined"
            description="暂无 PR"
          />
          <ul v-else class="pr-list">
            <li
              v-for="pr in pulls"
              :key="pr.number"
              class="pr-item"
            >
              <div class="pr-row">
                <component
                  :is="pr.merged ? CheckCircleOutlined : pr.state === 'open' ? ExclamationCircleOutlined : ClockCircleOutlined"
                  class="pr-state-icon"
                  :class="{
                    'state-open': pr.state === 'open' && !pr.merged,
                    'state-merged': pr.merged,
                    'state-closed': pr.state === 'closed' && !pr.merged,
                  }"
                />
                <a-avatar :src="pr.avatarUrl" :size="28" class="pr-avatar" />
                <div class="pr-info">
                  <div class="pr-title-line">
                    <span class="pr-number">#{{ pr.number }}</span>
                    <span class="pr-title">{{ pr.title }}</span>
                    <a-tag v-for="l in pr.labels" :key="l" class="pr-label">{{ l }}</a-tag>
                  </div>
                  <div class="pr-sub">
                    <span>{{ pr.author }}</span>
                    <span class="pr-time">{{ formatTime(pr.updatedAt) }}</span>
                  </div>
                </div>
                <a-button
                  type="primary"
                  size="small"
                  :loading="analyzingIds.has(pr.number)"
                  class="analyze-btn"
                  @click="analyzePr(pr)"
                >
                  分析
                </a-button>
              </div>
            </li>
          </ul>
        </a-spin>

        <div v-if="total > 0" class="pr-pagination">
          <a-pagination
            size="small"
            :current="page"
            :page-size="pageSize"
            :total="total"
            :show-total="(t: number) => `共 ${t} 条`"
            @change="onPageChange"
          />
        </div>
      </a-card>
    </div>
  </div>
</template>

<style scoped>
.repo-page {
  min-height: 100vh;
  background: #f0f2f5;
  padding: 24px 16px 64px;
}
.container {
  max-width: 960px;
  margin: 0 auto;
}
.page-title {
  font-size: 22px;
  font-weight: 600;
  color: rgba(0,0,0,0.88);
  margin-bottom: 4px;
}
.page-desc {
  font-size: 14px;
  color: rgba(0,0,0,0.55);
  margin-bottom: 20px;
}
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.repo-input {
  flex: 1;
}
.error-alert {
  margin-bottom: 16px;
}
.repo-card {
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
  margin-bottom: 16px;
}
.repo-header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.owner-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}
.repo-meta {
  flex: 1;
  min-width: 0;
}
.repo-name {
  font-size: 18px;
  font-weight: 600;
  color: rgba(0,0,0,0.88);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}
.repo-name a {
  color: #1677ff;
  text-decoration: none;
}
.repo-name a:hover {
  text-decoration: underline;
}
.private-tag {
  margin: 0;
  font-size: 12px;
}
.repo-desc {
  color: rgba(0,0,0,0.65);
  font-size: 14px;
  margin-bottom: 8px;
}
.repo-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: rgba(0,0,0,0.65);
}
.lang {
  padding: 2px 8px;
  border-radius: 10px;
  background: #e6f4ff;
  color: #1677ff;
  font-size: 12px;
}
.repo-topics {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.topic-tag {
  margin: 0;
  font-size: 12px;
  border-radius: 10px;
  background: #f0f5ff;
  border-color: #adc6ff;
  color: #2f54eb;
}
.pulls-card {
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
}
.pulls-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.pr-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.pr-item {
  border-radius: 6px;
  padding: 10px 8px;
  transition: background .15s;
}
.pr-item:hover {
  background: #fafafa;
}
.pr-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pr-state-icon {
  font-size: 18px;
  flex-shrink: 0;
}
.state-open { color: #52c41a; }
.state-merged { color: #722ed1; }
.state-closed { color: #8c8c8c; }
.pr-avatar {
  flex-shrink: 0;
}
.pr-info {
  flex: 1;
  min-width: 0;
}
.pr-title-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 2px;
}
.pr-number {
  color: rgba(0,0,0,0.45);
  font-size: 13px;
  flex-shrink: 0;
}
.pr-title {
  font-size: 14px;
  font-weight: 500;
  color: rgba(0,0,0,0.85);
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pr-label {
  margin: 0;
  font-size: 11px;
  flex-shrink: 0;
}
.pr-sub {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: rgba(0,0,0,0.45);
}
.pr-time {
  flex-shrink: 0;
}
.analyze-btn {
  flex-shrink: 0;
}
.pr-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
.history-card {
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  margin-bottom: 16px;
}
.history-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}
.history-title-text {
  margin: 0 2px;
}
.history-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.history-item:hover {
  background: #f5f5f5;
}
.history-avatar {
  flex-shrink: 0;
}
.history-info {
  flex: 1;
  min-width: 0;
}
.history-name {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.history-fullname {
  font-size: 14px;
  font-weight: 500;
  color: #1677ff;
}
.history-private {
  margin: 0;
  font-size: 11px;
}
.history-lang {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
  padding: 1px 6px;
  border-radius: 8px;
  background: #f0f5ff;
}
.history-sub {
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}
.history-desc {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.history-time {
  flex-shrink: 0;
}
.history-remove-btn {
  flex-shrink: 0;
}
</style>
