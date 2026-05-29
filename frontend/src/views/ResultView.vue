<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeftOutlined, FileTextOutlined, WarningOutlined, BulbOutlined } from '@ant-design/icons-vue'
import type { ReviewResponse } from '@/api/review'
import RiskItem from '@/components/RiskItem.vue'

const props = defineProps<{ review: ReviewResponse }>()
defineEmits<{
  (e: 'reset'): void
}>()

const isError = computed(() => props.review.status === 'error')
const sortedRisks = computed(() => {
  const order = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 } as const
  return [...props.review.risks].sort(
    (a, b) => (order[a.level] ?? 9) - (order[b.level] ?? 9),
  )
})

const riskStats = computed(() => {
  const stats = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
  for (const r of props.review.risks) {
    if (r.level in stats) stats[r.level]++
  }
  return stats
})
</script>

<template>
  <div class="result">
    <div class="container">
      <a-page-header
        class="page-header"
        :title="review.prTitle || '(无标题)'"
        :sub-title="`#${review.id}`"
        @back="$emit('reset')"
      >
        <template #backIcon>
          <ArrowLeftOutlined />
        </template>
        <template #tags>
          <a-tag :color="isError ? 'error' : 'success'">
            {{ isError ? '分析失败' : '分析完成' }}
          </a-tag>
        </template>
        <template #extra>
          <a-button @click="$emit('reset')">
            <template #icon><ArrowLeftOutlined /></template>
            重新分析
          </a-button>
        </template>

        <a-descriptions size="small" :column="{ xs: 1, sm: 2, md: 3 }">
          <a-descriptions-item label="作者">
            {{ review.author || '未知' }}
          </a-descriptions-item>
          <a-descriptions-item label="风险总数">
            {{ review.risks.length }}
          </a-descriptions-item>
          <a-descriptions-item label="改进建议数">
            {{ review.suggestions.length }}
          </a-descriptions-item>
        </a-descriptions>

        <div v-if="!isError && review.risks.length" class="risk-stats">
          <a-tag color="red">严重 {{ riskStats.CRITICAL }}</a-tag>
          <a-tag color="volcano">高 {{ riskStats.HIGH }}</a-tag>
          <a-tag color="gold">中 {{ riskStats.MEDIUM }}</a-tag>
          <a-tag color="blue">低 {{ riskStats.LOW }}</a-tag>
        </div>
      </a-page-header>

      <a-card :bordered="false" class="section">
        <template #title>
          <FileTextOutlined />
          <span class="section-title-text">变更摘要</span>
        </template>
        <a-alert
          v-if="isError"
          type="error"
          show-icon
          :message="review.summary || '（无摘要）'"
        />
        <a-typography-paragraph v-else class="summary">
          {{ review.summary || '（无摘要）' }}
        </a-typography-paragraph>
      </a-card>

      <a-card :bordered="false" class="section">
        <template #title>
          <WarningOutlined />
          <span class="section-title-text">风险列表</span>
          <a-badge
            :count="review.risks.length"
            :number-style="{ backgroundColor: '#1677ff' }"
            class="title-badge"
          />
        </template>
        <div v-if="sortedRisks.length" class="risk-list">
          <RiskItem
            v-for="(risk, i) in sortedRisks"
            :key="`${risk.file}-${risk.line ?? 'na'}-${i}`"
            :risk="risk"
          />
        </div>
        <a-empty v-else description="未检出风险项" />
      </a-card>

      <a-card :bordered="false" class="section">
        <template #title>
          <BulbOutlined />
          <span class="section-title-text">改进建议</span>
          <a-badge
            :count="review.suggestions.length"
            :number-style="{ backgroundColor: '#1677ff' }"
            class="title-badge"
          />
        </template>
        <a-list
          v-if="review.suggestions.length"
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
</style>
