<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReviewTaskStore } from '@/stores/reviewTasks'
import ResultView from '@/views/ResultView.vue'

const route = useRoute()
const router = useRouter()
const taskStore = useReviewTaskStore()

let manualPollTimer: ReturnType<typeof setInterval> | null = null

watch(
  () => route.params.id,
  async (id) => {
    if (typeof id !== 'string') return
    const existing = taskStore.tasks.find((t) => t.id === id)
    if (existing) {
      taskStore.ensurePolling(existing)
    } else {
      // 历史记录中没有：首次访问/页面刷新场景，主动拉一次并交给 store
      await taskStore.refreshOne(id)
    }
    // 兜底：详情页打开期间，每 2s 主动同步一次（store 内部也会轮询，这里只是保证视图响应）
    if (manualPollTimer) clearInterval(manualPollTimer)
    manualPollTimer = setInterval(() => {
      const t = taskStore.tasks.find((x) => x.id === id)
      if (!t) return
      if (t.status === 'completed' || t.status === 'error') {
        if (manualPollTimer) clearInterval(manualPollTimer)
        manualPollTimer = null
      }
    }, 2000)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (manualPollTimer) clearInterval(manualPollTimer)
  manualPollTimer = null
})

function onReset() {
  router.push({ name: 'home' })
}
</script>

<template>
  <ResultView :review-id="(route.params.id as string)" @reset="onReset" />
</template>
