<script setup lang="ts">
import { computed } from 'vue'
import type { RiskItem, RiskLevel } from '@/api/review'

const props = defineProps<{ risk: RiskItem }>()

const levelMeta: Record<RiskLevel, { label: string; color: string }> = {
  CRITICAL: { label: '严重', color: 'red' },
  HIGH:     { label: '高',   color: 'volcano' },
  MEDIUM:   { label: '中',   color: 'gold' },
  LOW:      { label: '低',   color: 'blue' },
}

const meta = computed(() => levelMeta[props.risk.level] ?? { label: props.risk.level, color: 'default' })
</script>

<template>
  <div class="risk-item">
    <div class="risk-head">
      <a-tag :color="meta.color" class="risk-level">{{ meta.label }}</a-tag>
      <span class="risk-file">
        {{ risk.file }}<span v-if="risk.line != null" class="risk-line"> : L{{ risk.line }}</span>
      </span>
    </div>
    <div class="risk-desc">{{ risk.description }}</div>
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
</style>
