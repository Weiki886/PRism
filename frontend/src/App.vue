<script setup lang="ts">
import { ref } from 'vue'
import { ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import HomeView from '@/views/HomeView.vue'
import ResultView from '@/views/ResultView.vue'
import type { ReviewResponse } from '@/api/review'

const review = ref<ReviewResponse | null>(null)

const themeConfig = {
  token: {
    colorPrimary: '#1677ff',
    colorInfo: '#1677ff',
    borderRadius: 6,
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
  },
}

function onSuccess(data: ReviewResponse) {
  review.value = data
  window.scrollTo({ top: 0 })
}

function onReset() {
  review.value = null
}
</script>

<template>
  <ConfigProvider :locale="zhCN" :theme="themeConfig">
    <ResultView v-if="review" :review="review" @reset="onReset" />
    <HomeView v-else @success="onSuccess" />
  </ConfigProvider>
</template>

<style>
* { box-sizing: border-box; }
html, body, #app {
  margin: 0;
  padding: 0;
  min-height: 100%;
}
body {
  background: #f0f2f5;
  color: rgba(0, 0, 0, 0.88);
  -webkit-font-smoothing: antialiased;
}
</style>
