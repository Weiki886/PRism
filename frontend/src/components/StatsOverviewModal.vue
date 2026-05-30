<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  BarChartOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  LoadingOutlined,
  ReloadOutlined,
  WarningFilled,
} from '@ant-design/icons-vue'
import {
  getFeedbackOverview,
  getReviewStats,
  type FeedbackOverview,
  type ReviewStats,
} from '@/api/stats'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()

const reviewStats = ref<ReviewStats | null>(null)
const feedbackOverview = ref<FeedbackOverview | null>(null)
const loading = ref(false)
const reviewStatsError = ref('')
const feedbackOverviewError = ref('')

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

watch(
  () => props.open,
  (v) => {
    if (v) loadAll()
  },
)

function extractMessage(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { message?: string } }; message?: string }
  return e?.response?.data?.message ?? e?.message ?? fallback
}

async function loadAll() {
  loading.value = true
  reviewStatsError.value = ''
  feedbackOverviewError.value = ''
  const [statsRes, overviewRes] = await Promise.allSettled([
    getReviewStats(),
    getFeedbackOverview(),
  ])
  if (statsRes.status === 'fulfilled') {
    reviewStats.value = statsRes.value
  } else {
    reviewStatsError.value = extractMessage(statsRes.reason, '加载评审统计失败')
  }
  if (overviewRes.status === 'fulfilled') {
    feedbackOverview.value = overviewRes.value
  } else {
    feedbackOverviewError.value = extractMessage(overviewRes.reason, '加载误报率统计失败')
  }
  loading.value = false
}

const completionRate = computed(() => {
  const s = reviewStats.value
  if (!s || s.totalReviews === 0) return 0
  return Math.round((s.completedReviews / s.totalReviews) * 1000) / 10
})

const fpRatePercent = computed(() => {
  const o = feedbackOverview.value
  if (!o) return 0
  return Math.round(o.falsePositiveRate * 1000) / 10
})

const fpRateStatus = computed<'success' | 'normal' | 'exception'>(() => {
  const r = feedbackOverview.value?.falsePositiveRate ?? 0
  if (r >= 0.5) return 'exception'
  if (r >= 0.2) return 'normal'
  return 'success'
})

const riskLevels = [
  { key: 'CRITICAL', label: '严重', color: '#ff4d4f' },
  { key: 'HIGH', label: '高', color: '#fa541c' },
  { key: 'MEDIUM', label: '中', color: '#faad14' },
  { key: 'LOW', label: '低', color: '#1677ff' },
] as const
</script>

<template>
  <a-modal
    v-model:open="visible"
    title="我的评审统计"
    :width="640"
    :footer="null"
    centered
  >
    <a-spin :spinning="loading">
      <div class="actions">
        <a-button size="small" :loading="loading" @click="loadAll">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>

      <a-alert
        v-if="reviewStatsError"
        type="error"
        show-icon
        :message="`评审统计加载失败：${reviewStatsError}`"
        class="error-alert"
      />

      <div class="section-title">
        <BarChartOutlined />
        <span>评审概览</span>
      </div>

      <a-row :gutter="[12, 12]" class="stat-row">
        <a-col :span="6">
          <div class="stat-card">
            <div class="stat-label">总数</div>
            <div class="stat-value">{{ reviewStats?.totalReviews ?? '—' }}</div>
          </div>
        </a-col>
        <a-col :span="6">
          <div class="stat-card success">
            <div class="stat-label">
              <CheckCircleFilled />
              已完成
            </div>
            <div class="stat-value">{{ reviewStats?.completedReviews ?? '—' }}</div>
          </div>
        </a-col>
        <a-col :span="6">
          <div class="stat-card processing">
            <div class="stat-label">
              <LoadingOutlined />
              进行中
            </div>
            <div class="stat-value">{{ reviewStats?.processingReviews ?? '—' }}</div>
          </div>
        </a-col>
        <a-col :span="6">
          <div class="stat-card error">
            <div class="stat-label">
              <CloseCircleFilled />
              失败
            </div>
            <div class="stat-value">{{ reviewStats?.errorReviews ?? '—' }}</div>
          </div>
        </a-col>
      </a-row>

      <div v-if="reviewStats && reviewStats.totalReviews > 0" class="rate-block">
        <div class="rate-label">
          <span>完成率</span>
          <span class="rate-value">{{ completionRate }}%</span>
        </div>
        <a-progress
          :percent="completionRate"
          :show-info="false"
          stroke-color="#52c41a"
        />
      </div>

      <a-divider class="divider" />

      <div class="section-title">
        <WarningFilled style="color: #fa541c" />
        <span>累计风险（{{ reviewStats?.totalRisks ?? 0 }}）</span>
      </div>

      <div v-if="reviewStats && reviewStats.totalRisks > 0" class="risk-bars">
        <div v-for="lv in riskLevels" :key="lv.key" class="risk-bar-row">
          <span class="risk-bar-label">{{ lv.label }}</span>
          <div class="risk-bar-track">
            <div
              class="risk-bar-fill"
              :style="{
                width: `${
                  ((reviewStats.riskLevelDistribution[lv.key] ?? 0) /
                    reviewStats.totalRisks) *
                  100
                }%`,
                background: lv.color,
              }"
            />
          </div>
          <span class="risk-bar-count">
            {{ reviewStats.riskLevelDistribution[lv.key] ?? 0 }}
          </span>
        </div>
      </div>
      <a-empty
        v-else-if="reviewStats"
        :image="undefined"
        description="尚未发现风险"
      />

      <a-divider class="divider" />

      <div class="section-title">
        <span>我的反馈与误报率</span>
      </div>

      <a-alert
        v-if="feedbackOverviewError"
        type="error"
        show-icon
        :message="`误报率统计加载失败：${feedbackOverviewError}`"
        class="error-alert"
      />

      <a-row :gutter="[12, 12]" class="stat-row">
        <a-col :span="8">
          <div class="stat-card">
            <div class="stat-label">反馈总数</div>
            <div class="stat-value">{{ feedbackOverview?.totalFeedbacks ?? '—' }}</div>
          </div>
        </a-col>
        <a-col :span="8">
          <div class="stat-card success">
            <div class="stat-label">已确认</div>
            <div class="stat-value">{{ feedbackOverview?.confirmedCount ?? '—' }}</div>
          </div>
        </a-col>
        <a-col :span="8">
          <div class="stat-card warning">
            <div class="stat-label">标为误报</div>
            <div class="stat-value">
              {{ feedbackOverview?.falsePositiveCount ?? '—' }}
            </div>
          </div>
        </a-col>
      </a-row>

      <div v-if="feedbackOverview && feedbackOverview.totalFeedbacks > 0" class="rate-block">
        <div class="rate-label">
          <span>误报率</span>
          <span class="rate-value">{{ fpRatePercent }}%</span>
        </div>
        <a-progress :percent="fpRatePercent" :status="fpRateStatus" />
        <div class="rate-hint">
          误报率越低，说明 AI 命中越准确。当前样本：{{ feedbackOverview.totalFeedbacks }} 条反馈。
        </div>
      </div>
      <a-empty
        v-else-if="feedbackOverview && !feedbackOverviewError"
        :image="undefined"
        description="还没有提交过反馈"
      />
    </a-spin>
  </a-modal>
</template>

<style scoped>
.error-alert {
  margin-bottom: 12px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 10px;
}
.stat-row {
  margin-bottom: 8px;
}
.stat-card {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 12px 14px;
  text-align: center;
}
.stat-card.success {
  background: #f6ffed;
  border-color: #b7eb8f;
}
.stat-card.processing {
  background: #e6f4ff;
  border-color: #91caff;
}
.stat-card.error {
  background: #fff2f0;
  border-color: #ffccc7;
}
.stat-card.warning {
  background: #fff7e6;
  border-color: #ffd591;
}
.stat-label {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
  margin-bottom: 6px;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  line-height: 1.2;
}
.rate-block {
  margin-top: 14px;
}
.rate-label {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
  margin-bottom: 4px;
}
.rate-value {
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}
.rate-hint {
  margin-top: 6px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
.divider {
  margin: 18px 0;
}
.risk-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.risk-bar-row {
  display: grid;
  grid-template-columns: 36px 1fr 36px;
  align-items: center;
  gap: 10px;
}
.risk-bar-label {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}
.risk-bar-track {
  height: 10px;
  background: #f5f5f5;
  border-radius: 5px;
  overflow: hidden;
}
.risk-bar-fill {
  height: 100%;
  border-radius: 5px;
  transition: width .3s;
}
.risk-bar-count {
  font-size: 13px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
  text-align: right;
}
</style>
