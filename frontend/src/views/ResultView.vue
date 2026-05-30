<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  ArrowLeftOutlined,
  FileTextOutlined,
  WarningOutlined,
  BulbOutlined,
  LoadingOutlined,
  DeleteOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import { deleteReview, getReview, retryReview, type ReviewResponse } from '@/api/review'
import {
  getFeedbackStats,
  submitFeedback,
  type FeedbackType,
  type RiskFeedbackStat,
} from '@/api/feedback'
import { useReviewTaskStore } from '@/stores/reviewTasks'
import RiskItem from '@/components/RiskItem.vue'
import HealthScoreCard from '@/components/HealthScoreCard.vue'

const props = defineProps<{ reviewId: string }>()
const emit = defineEmits<{
  (e: 'reset'): void
  (e: 'deleted', reviewId: string): void
}>()

const taskStore = useReviewTaskStore()

const review = ref<ReviewResponse | null>(null)
const feedbackStats = ref<RiskFeedbackStat[]>([])
const fetchError = ref('')
const feedbackLoading = ref(false)
const elapsed = ref(0)

let elapsedTimer: ReturnType<typeof setInterval> | null = null
let stopped = false

const task = computed(() => taskStore.tasks.find((t) => t.id === props.reviewId))

const status = computed<'submitting' | 'pending' | 'processing' | 'completed' | 'error'>(() => {
  if (review.value) return review.value.status
  return task.value?.status ?? 'pending'
})
const isPending = computed(
  () => status.value === 'submitting' || status.value === 'pending' || status.value === 'processing',
)
const isError = computed(() => status.value === 'error')
const isCompleted = computed(() => status.value === 'completed')

const submitError = computed(() =>
  task.value?.status === 'error' && !review.value ? task.value.submitError : '',
)

const progressPercent = computed(() => {
  if (isCompleted.value) return 100
  if (isError.value) return 100
  if (status.value === 'processing') return 65
  if (status.value === 'pending') return 35
  return 15
})

const statusText = computed(() => {
  switch (status.value) {
    case 'submitting':
      return '正在提交分析任务'
    case 'pending':
      return '排队中：任务已提交，等待开始分析'
    case 'processing':
      return 'AI 正在阅读代码变更并生成报告'
    case 'completed':
      return '分析完成'
    case 'error':
      return '分析失败'
    default:
      return ''
  }
})

const sortedRisks = computed(() => {
  if (!review.value) return []
  const order = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 } as const
  return [...review.value.risks].sort(
    (a, b) => (order[a.level] ?? 9) - (order[b.level] ?? 9),
  )
})

const riskStats = computed(() => {
  const stats = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
  if (!review.value) return stats
  for (const r of review.value.risks) {
    if (r.level in stats) stats[r.level]++
  }
  return stats
})

startElapsedTimer()

watch(
  () => props.reviewId,
  (id) => {
    review.value = null
    feedbackStats.value = []
    fetchError.value = ''
    elapsed.value = 0
    if (id) fetchOnce()
  },
  { immediate: true },
)

watch(
  () => task.value?.status,
  (s) => {
    // store 轮询拿到 completed/error 后，刷一次完整数据用于渲染详情
    if (s === 'completed' || s === 'error') {
      fetchOnce()
    }
  },
)

onBeforeUnmount(() => {
  stopped = true
  clearTimers()
})

function clearTimers() {
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

function startElapsedTimer() {
  if (elapsedTimer) return
  elapsedTimer = setInterval(() => {
    elapsed.value += 1
  }, 1000)
}

async function fetchOnce() {
  if (stopped || !props.reviewId) return
  try {
    const data = await getReview(props.reviewId)
    review.value = data
    fetchError.value = ''
    if (data.status === 'completed') {
      clearTimers()
      await loadFeedbackStats()
    } else if (data.status === 'error') {
      clearTimers()
    }
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } }; message?: string }
    fetchError.value =
      e?.response?.data?.message ??
      e?.message ??
      '获取分析结果失败'
  }
}

async function loadFeedbackStats() {
  if (!props.reviewId) return
  try {
    feedbackStats.value = await getFeedbackStats(props.reviewId)
  } catch {
    // 静默失败
  }
}

async function handleFeedback(riskIndex: number, feedback: FeedbackType) {
  if (!props.reviewId) return
  feedbackLoading.value = true
  try {
    await submitFeedback(props.reviewId, { riskIndex, feedback })
    message.success(feedback === 'CONFIRMED' ? '已确认该风险' : '已标记为误报')
    await loadFeedbackStats()
  } catch {
    // 拦截器已处理错误提示
  } finally {
    feedbackLoading.value = false
  }
}

function getFeedbackStatByIndex(index: number): RiskFeedbackStat | undefined {
  return feedbackStats.value.find((s) => s.riskIndex === index)
}

function handleDelete() {
  if (!props.reviewId) return
  Modal.confirm({
    title: '删除该评审记录？',
    content: '将从服务器永久删除该记录，删除后无法恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        const localTask = taskStore.tasks.find((t) => t.id === props.reviewId)
        if (localTask) {
          await taskStore.deleteRemote(localTask.localId)
        } else {
          await deleteReview(props.reviewId)
        }
        message.success('已删除')
        emit('deleted', props.reviewId)
      } catch {
        // 拦截器已提示
      }
    },
  })
}

function handleRetry() {
  if (!props.reviewId) return
  const isCompletedRetry = isCompleted.value
  Modal.confirm({
    title: isCompletedRetry ? '重新分析该 PR？' : '重新分析该 PR？',
    content: isCompletedRetry
      ? '将基于原 PR 链接重新触发分析，新结果会覆盖现有的风险列表与改进建议。'
      : '将基于原 PR 链接重新触发分析。',
    okText: '重新分析',
    cancelText: '取消',
    async onOk() {
      try {
        const localTask = taskStore.tasks.find((t) => t.id === props.reviewId)
        if (localTask) {
          await taskStore.retry(localTask.localId)
        } else {
          await retryReview(props.reviewId)
          // 兜底：详情页会通过 watch task.status 重新拉数据
          await taskStore.refreshOne(props.reviewId)
        }
        // 重置本地视图，让进度卡片重新显示
        review.value = null
        feedbackStats.value = []
        fetchError.value = ''
        elapsed.value = 0
        startElapsedTimer()
        message.success('已重新加入分析队列')
      } catch {
        // 拦截器已提示（含 409 REVIEW_IN_PROGRESS）
      }
    },
  })
}

const headerTagColor = computed(() => {
  if (isError.value) return 'error'
  if (isCompleted.value) return 'success'
  return 'processing'
})
const headerTagText = computed(() => {
  if (isError.value) return '分析失败'
  if (isCompleted.value) return '分析完成'
  if (status.value === 'processing') return '分析中'
  if (status.value === 'pending') return '排队中'
  return '提交中'
})
const headerSubTitle = computed(() => `#${props.reviewId}`)
</script>

<template>
  <div class="result">
    <div class="container">
      <a-page-header
        class="page-header"
        :title="review?.prTitle || (isError ? '分析失败' : '正在准备分析…')"
        :sub-title="headerSubTitle"
        @back="$emit('reset')"
      >
        <template #backIcon>
          <ArrowLeftOutlined />
        </template>
        <template #tags>
          <a-tag :color="headerTagColor">{{ headerTagText }}</a-tag>
        </template>
        <template #extra>
          <a-space>
            <a-button
              v-if="isError"
              type="primary"
              @click="handleRetry"
            >
              <template #icon><ReloadOutlined /></template>
              重新分析
            </a-button>
            <a-button
              v-else-if="isCompleted"
              @click="handleRetry"
            >
              <template #icon><ReloadOutlined /></template>
              重新分析
            </a-button>
            <a-button
              v-if="isCompleted || isError"
              danger
              @click="handleDelete"
            >
              <template #icon><DeleteOutlined /></template>
              删除记录
            </a-button>
            <a-button @click="$emit('reset')">
              <template #icon><ArrowLeftOutlined /></template>
              返回首页
            </a-button>
          </a-space>
        </template>

        <a-descriptions size="small" :column="{ xs: 1, sm: 2, md: 3 }">
          <a-descriptions-item label="作者">
            {{ review?.author || '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="风险总数">
            {{ isCompleted ? review?.risks.length ?? 0 : '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="改进建议数">
            {{ isCompleted ? review?.suggestions.length ?? 0 : '—' }}
          </a-descriptions-item>
        </a-descriptions>

        <div v-if="isCompleted && review && review.risks.length" class="risk-stats">
          <a-tag color="red">严重 {{ riskStats.CRITICAL }}</a-tag>
          <a-tag color="volcano">高 {{ riskStats.HIGH }}</a-tag>
          <a-tag color="gold">中 {{ riskStats.MEDIUM }}</a-tag>
          <a-tag color="blue">低 {{ riskStats.LOW }}</a-tag>
        </div>
      </a-page-header>

      <a-card v-if="submitError" :bordered="false" class="section">
        <a-alert
          type="error"
          show-icon
          :message="submitError"
          description="可返回首页修改 PR 链接后重试。"
        />
      </a-card>

      <a-card v-else-if="isPending" :bordered="false" class="section progress-card">
        <div class="progress-head">
          <LoadingOutlined class="progress-icon" spin />
          <div>
            <div class="progress-title">{{ statusText }}</div>
            <div class="progress-sub">
              已用时 {{ elapsed }}s。系统每 2 秒自动刷新一次状态，期间可返回首页提交其他 PR。
            </div>
          </div>
        </div>
        <a-progress
          :percent="progressPercent"
          :show-info="false"
          status="active"
          stroke-color="#1677ff"
        />
        <a-alert
          v-if="fetchError"
          class="progress-alert"
          type="warning"
          show-icon
          :message="fetchError"
        />
      </a-card>

      <a-card
        v-if="!submitError && isCompleted && review && review.healthScore != null && review.mergeAdvice"
        :bordered="false"
        class="section health-section"
        :body-style="{ padding: 0 }"
      >
        <HealthScoreCard
          :score="review.healthScore"
          :advice="review.mergeAdvice"
        />
      </a-card>

      <a-card v-if="!submitError" :bordered="false" class="section">
        <template #title>
          <FileTextOutlined />
          <span class="section-title-text">变更摘要</span>
        </template>
        <a-skeleton v-if="isPending" active :paragraph="{ rows: 3 }" />
        <a-alert
          v-else-if="isError"
          type="error"
          show-icon
          :message="review?.summary || '分析失败，请稍后重试'"
        />
        <a-typography-paragraph v-else class="summary">
          {{ review?.summary || '（无摘要）' }}
        </a-typography-paragraph>
      </a-card>

      <a-card v-if="!submitError && !isError" :bordered="false" class="section">
        <template #title>
          <WarningOutlined />
          <span class="section-title-text">风险列表</span>
          <a-badge
            v-if="isCompleted"
            :count="review?.risks.length ?? 0"
            :number-style="{ backgroundColor: '#1677ff' }"
            class="title-badge"
          />
        </template>
        <a-skeleton v-if="isPending" active :paragraph="{ rows: 4 }" />
        <a-spin v-else :spinning="feedbackLoading">
          <div v-if="sortedRisks.length" class="risk-list">
            <RiskItem
              v-for="(risk, i) in sortedRisks"
              :key="`${risk.file}-${risk.line ?? 'na'}-${i}`"
              :risk="risk"
              :risk-index="i"
              :feedback-stat="getFeedbackStatByIndex(i)"
              @feedback="handleFeedback"
            />
          </div>
          <a-empty v-else description="未检出风险项" />
        </a-spin>
      </a-card>

      <a-card v-if="!submitError && !isError" :bordered="false" class="section">
        <template #title>
          <BulbOutlined />
          <span class="section-title-text">改进建议</span>
          <a-badge
            v-if="isCompleted"
            :count="review?.suggestions.length ?? 0"
            :number-style="{ backgroundColor: '#1677ff' }"
            class="title-badge"
          />
        </template>
        <a-skeleton v-if="isPending" active :paragraph="{ rows: 3 }" />
        <a-list
          v-else-if="review && review.suggestions.length"
          size="small"
          :data-source="review.suggestions"
          :split="true"
        >
          <template #renderItem="{ item, index }">
            <a-list-item>
              <span class="suggestion-index">{{ index + 1 }}.</span>
              <span class="suggestion-text">{{ item }}</span>
            </a-list-item>
          </template>
        </a-list>
        <a-empty v-else description="暂无建议" />
      </a-card>
    </div>
  </div>
</template>

<style scoped>
.result {
  min-height: 100vh;
  background: #f0f2f5;
  padding: 24px 16px 64px;
}
.container {
  max-width: 960px;
  margin: 0 auto;
}
.page-header {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}
.risk-stats {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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
.title-badge {
  margin-left: 8px;
}
.summary {
  margin-bottom: 0 !important;
  line-height: 1.75;
  white-space: pre-wrap;
  color: rgba(0, 0, 0, 0.85);
}
.risk-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.suggestion-index {
  color: #1677ff;
  font-weight: 600;
  margin-right: 8px;
  flex-shrink: 0;
}
.suggestion-text {
  color: rgba(0, 0, 0, 0.85);
  line-height: 1.6;
}
.progress-card {
  border: 1px solid #e6f4ff;
  background: #f5faff;
}
.progress-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.progress-icon {
  font-size: 22px;
  color: #1677ff;
  margin-top: 2px;
}
.progress-title {
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
  font-size: 15px;
}
.progress-sub {
  color: rgba(0, 0, 0, 0.55);
  font-size: 13px;
  margin-top: 2px;
}
.progress-alert {
  margin-top: 12px;
}
</style>
