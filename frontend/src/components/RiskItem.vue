<script setup lang="ts">
import { computed } from 'vue'
import { LikeOutlined, DislikeOutlined } from '@ant-design/icons-vue'
import type { RiskItem, RiskLevel, ConfidenceLevel } from '@/api/review'
import type { RiskFeedbackStat, FeedbackType } from '@/api/feedback'

const props = defineProps<{
  risk: RiskItem
  riskIndex: number
  feedbackStat?: RiskFeedbackStat
}>()

const emit = defineEmits<{
  (e: 'feedback', riskIndex: number, feedback: FeedbackType): void
}>()

const levelMeta: Record<RiskLevel, { label: string; color: string }> = {
  CRITICAL: { label: '严重', color: 'red' },
  HIGH:     { label: '高',   color: 'volcano' },
  MEDIUM:   { label: '中',   color: 'gold' },
  LOW:      { label: '低',   color: 'blue' },
}

const confidenceMeta: Record<ConfidenceLevel, { label: string; color: string }> = {
  HIGH:   { label: '高置信', color: 'green' },
  MEDIUM: { label: '中置信', color: 'orange' },
  LOW:    { label: '低置信', color: 'default' },
}

const meta = computed(() => levelMeta[props.risk.level] ?? { label: props.risk.level, color: 'default' })
const confidenceMeta2 = computed(() => confidenceMeta[props.risk.confidence] ?? { label: props.risk.confidence, color: 'default' })

const myFeedback = computed(() => props.feedbackStat?.myFeedback)
const falsePositiveCount = computed(() => props.feedbackStat?.falsePositiveCount ?? 0)
const confirmedCount = computed(() => props.feedbackStat?.confirmedCount ?? 0)

function handleFeedback(feedback: FeedbackType) {
  emit('feedback', props.riskIndex, feedback)
}
</script>

<template>
  <div class="risk-item">
    <div class="risk-head">
      <a-tag :color="meta.color" class="risk-level">{{ meta.label }}</a-tag>
      <a-tag :color="confidenceMeta2.color" class="confidence-tag">{{ confidenceMeta2.label }}</a-tag>
      <span class="risk-file">
        {{ risk.file }}<span v-if="risk.line != null" class="risk-line"> : L{{ risk.line }}</span>
      </span>
    </div>
    <div class="risk-desc">{{ risk.description }}</div>

    <div class="risk-footer">
      <a-space>
        <a-button
          size="small"
          :type="myFeedback === 'CONFIRMED' ? 'primary' : 'default'"
          @click="handleFeedback('CONFIRMED')"
        >
          <template #icon><LikeOutlined /></template>
          确认 {{ confirmedCount > 0 ? `(${confirmedCount})` : '' }}
        </a-button>
        <a-button
          size="small"
          :type="myFeedback === 'FALSE_POSITIVE' ? 'primary' : 'default'"
          danger
          @click="handleFeedback('FALSE_POSITIVE')"
        >
          <template #icon><DislikeOutlined /></template>
          误报 {{ falsePositiveCount > 0 ? `(${falsePositiveCount})` : '' }}
        </a-button>
      </a-space>
    </div>
  </div>
</template>

<style scoped>
.risk-item {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 12px 16px;
  background: #fff;
  transition: border-color .2s, box-shadow .2s;
}
.risk-item:hover {
  border-color: #1677ff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.08);
}
.risk-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.risk-level {
  margin-right: 0;
  font-weight: 600;
}
.confidence-tag {
  margin-right: 0;
  font-size: 12px;
}
.risk-file {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
  word-break: break-all;
}
.risk-line {
  color: #1677ff;
}
.risk-desc {
  margin-top: 8px;
  color: rgba(0, 0, 0, 0.85);
  line-height: 1.6;
  font-size: 14px;
}
.risk-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}
</style>
