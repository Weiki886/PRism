<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  LikeOutlined,
  DislikeOutlined,
  BulbOutlined,
  CopyOutlined,
  DownOutlined,
  UpOutlined,
} from '@ant-design/icons-vue'
import type { RiskItem, RiskLevel, ConfidenceLevel, RiskSource } from '@/api/review'
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

const sourceMeta: Record<RiskSource, { label: string; color: string; tip: string }> = {
  AI:   { label: 'AI 分析',  color: 'purple', tip: '由 AI 模型语义分析得出' },
  RULE: { label: '规则引擎', color: 'cyan',   tip: '由静态规则扫描器命中，确定性强' },
}

const meta = computed(() => levelMeta[props.risk.level] ?? { label: props.risk.level, color: 'default' })
const confidenceMeta2 = computed(() => confidenceMeta[props.risk.confidence] ?? { label: props.risk.confidence, color: 'default' })
const sourceMeta2 = computed(() =>
  props.risk.source ? sourceMeta[props.risk.source] : null,
)

const myFeedback = computed(() => props.feedbackStat?.myFeedback)
const falsePositiveCount = computed(() => props.feedbackStat?.falsePositiveCount ?? 0)
const confirmedCount = computed(() => props.feedbackStat?.confirmedCount ?? 0)

const hasSuggestedFix = computed(
  () => !!props.risk.suggestedFix && props.risk.suggestedFix.trim().length > 0,
)
const fixExpanded = ref(false)

function handleFeedback(feedback: FeedbackType) {
  emit('feedback', props.riskIndex, feedback)
}

async function copyFix() {
  if (!props.risk.suggestedFix) return
  try {
    await navigator.clipboard.writeText(props.risk.suggestedFix)
    message.success('已复制修复代码')
  } catch {
    message.error('复制失败，请手动选择文本复制')
  }
}
</script>

<template>
  <div class="risk-item">
    <div class="risk-head">
      <a-tag :color="meta.color" class="risk-level">{{ meta.label }}</a-tag>
      <a-tag :color="confidenceMeta2.color" class="confidence-tag">{{ confidenceMeta2.label }}</a-tag>
      <a-tooltip v-if="sourceMeta2" :title="sourceMeta2.tip">
        <a-tag :color="sourceMeta2.color" class="source-tag">{{ sourceMeta2.label }}</a-tag>
      </a-tooltip>
      <span class="risk-file">
        {{ risk.file }}<span v-if="risk.line != null" class="risk-line"> : L{{ risk.line }}</span>
      </span>
    </div>
    <div class="risk-desc">{{ risk.description }}</div>

    <div v-if="hasSuggestedFix" class="fix-block">
      <div class="fix-head" @click="fixExpanded = !fixExpanded">
        <BulbOutlined class="fix-icon" />
        <span class="fix-title">修复建议</span>
        <a-tag color="green" class="fix-tag">AI</a-tag>
        <span class="fix-toggle">
          <component :is="fixExpanded ? UpOutlined : DownOutlined" />
        </span>
      </div>
      <div v-if="fixExpanded" class="fix-body">
        <div class="fix-toolbar">
          <a-button size="small" type="text" @click.stop="copyFix">
            <template #icon><CopyOutlined /></template>
            复制
          </a-button>
        </div>
        <pre class="fix-code"><code>{{ risk.suggestedFix }}</code></pre>
      </div>
    </div>

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
.source-tag {
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
.fix-block {
  margin-top: 10px;
  border: 1px solid #d9f7be;
  border-radius: 6px;
  background: #f6ffed;
  overflow: hidden;
}
.fix-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.85);
  transition: background .15s;
}
.fix-head:hover {
  background: #e6ffe0;
}
.fix-icon {
  color: #52c41a;
  font-size: 14px;
}
.fix-title {
  font-weight: 600;
}
.fix-tag {
  margin: 0 0 0 4px;
  font-size: 11px;
  line-height: 16px;
}
.fix-toggle {
  margin-left: auto;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}
.fix-body {
  border-top: 1px solid #d9f7be;
  background: #fff;
}
.fix-toolbar {
  display: flex;
  justify-content: flex-end;
  padding: 4px 8px;
  border-bottom: 1px solid #f0f0f0;
}
.fix-code {
  margin: 0;
  padding: 12px 14px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.6;
  color: rgba(0, 0, 0, 0.85);
  white-space: pre-wrap;
  word-break: break-word;
  background: #fafafa;
  max-height: 320px;
  overflow: auto;
}
.risk-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}
</style>
