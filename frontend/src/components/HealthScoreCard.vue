<script setup lang="ts">
import { computed } from 'vue'
import {
  CheckCircleFilled,
  ExclamationCircleFilled,
  CloseCircleFilled,
} from '@ant-design/icons-vue'
import type { MergeAdvice } from '@/api/review'

const props = defineProps<{
  score: number
  advice: MergeAdvice
}>()

interface AdviceMeta {
  label: string
  color: string
  bg: string
  border: string
  hint: string
  icon: typeof CheckCircleFilled
}

const adviceMeta: Record<MergeAdvice, AdviceMeta> = {
  RECOMMEND: {
    label: '推荐合并',
    color: '#52c41a',
    bg: '#f6ffed',
    border: '#b7eb8f',
    hint: '风险较少且严重程度低，可考虑合并',
    icon: CheckCircleFilled,
  },
  CAUTION: {
    label: '谨慎合并',
    color: '#faad14',
    bg: '#fffbe6',
    border: '#ffe58f',
    hint: '存在中高风险，建议处理后再合并',
    icon: ExclamationCircleFilled,
  },
  NOT_RECOMMEND: {
    label: '不推荐合并',
    color: '#ff4d4f',
    bg: '#fff2f0',
    border: '#ffccc7',
    hint: '存在严重风险，强烈建议修复后再合并',
    icon: CloseCircleFilled,
  },
}

const meta = computed<AdviceMeta>(() => adviceMeta[props.advice])

const progressColor = computed(() => {
  if (props.score >= 80) return '#52c41a'
  if (props.score >= 50) return '#faad14'
  return '#ff4d4f'
})

const scoreLabel = computed(() => {
  if (props.score >= 80) return '健康'
  if (props.score >= 50) return '一般'
  return '风险高'
})
</script>

<template>
  <div
    class="health-card"
    :style="{ background: meta.bg, borderColor: meta.border }"
  >
    <div class="score-block">
      <a-progress
        type="circle"
        :percent="score"
        :stroke-color="progressColor"
        :size="92"
        :stroke-width="8"
      >
        <template #format>
          <div class="score-inner">
            <div class="score-value" :style="{ color: progressColor }">
              {{ score }}
            </div>
            <div class="score-unit">/ 100</div>
          </div>
        </template>
      </a-progress>
    </div>
    <div class="info-block">
      <div class="info-head">
        <span class="info-title">PR 健康分</span>
        <a-tag :color="progressColor" class="score-tag">{{ scoreLabel }}</a-tag>
      </div>
      <div class="advice-row" :style="{ color: meta.color }">
        <component :is="meta.icon" class="advice-icon" />
        <span class="advice-label">{{ meta.label }}</span>
      </div>
      <div class="advice-hint">{{ meta.hint }}</div>
      <div class="rule-hint">
        基于风险数量与等级用规则计算（CRITICAL -25 / HIGH -15 / MEDIUM -7 / LOW -2），结果稳定可复现。
      </div>
    </div>
  </div>
</template>

<style scoped>
.health-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 20px;
  border: 1px solid;
  border-radius: 8px;
  transition: background .2s, border-color .2s;
}
.score-block {
  flex-shrink: 0;
}
.score-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1.1;
}
.score-value {
  font-size: 26px;
  font-weight: 700;
}
.score-unit {
  font-size: 11px;
  color: rgba(0, 0, 0, 0.45);
  margin-top: 2px;
}
.info-block {
  flex: 1;
  min-width: 0;
}
.info-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.info-title {
  font-size: 15px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}
.score-tag {
  margin: 0;
  font-size: 12px;
  border: none;
  color: #fff;
}
.advice-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 4px;
}
.advice-icon {
  font-size: 16px;
}
.advice-hint {
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
  line-height: 1.5;
}
.rule-hint {
  margin-top: 6px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
  line-height: 1.5;
}
@media (max-width: 480px) {
  .health-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
