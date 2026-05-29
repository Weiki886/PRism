<script setup lang="ts">
import { ref } from 'vue'
import HomeView from '@/views/HomeView.vue'
import ResultView from '@/views/ResultView.vue'
import type { ReviewResponse } from '@/api/review'

const review = ref<ReviewResponse | null>(null)

function onSuccess(data: ReviewResponse) {
  review.value = data
  window.scrollTo({ top: 0 })
}

function onReset() {
  review.value = null
}
</script>

<template>
  <ResultView v-if="review" :review="review" @reset="onReset" />
  <HomeView v-else @success="onSuccess" />
</template>
