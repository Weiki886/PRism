<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { GithubOutlined, ThunderboltOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import { useReviewTaskStore } from '@/stores/reviewTasks'

const taskStore = useReviewTaskStore()

const prUrl = ref('')
const errorMsg = ref('')

const PR_URL_RE = /^https:\/\/github\.com\/[^/]+\/[^/]+\/pull\/\d+/i

function onSubmit() {
  errorMsg.value = ''
  const url = prUrl.value.trim()
  if (!url) {
    errorMsg.value = '请输入 PR 链接'
    return
  }
  if (!PR_URL_RE.test(url)) {
    errorMsg.value = '链接格式不正确，应为 https://github.com/owner/repo/pull/123'
    return
  }
  // fire-and-forget：任务在 store 内同步入列，POST 与轮询在后台进行
  void taskStore.submit(url)
  message.success({
    content: '已加入分析队列，可在右上角"任务"中查看进度',
    duration: 3,
  })
  prUrl.value = ''
}
</script>

<template>
  <div class="home">
    <div class="brand">
      <div class="logo">
        <ThunderboltOutlined />
      </div>
      <div class="brand-text">
        <div class="brand-name">PRism</div>
        <div class="brand-desc">AI Code Review Platform</div>
      </div>
    </div>

    <a-card :bordered="false" class="card">
      <a-typography-title :level="3" class="title">
        开始一次 AI 代码审查
      </a-typography-title>
      <a-typography-paragraph type="secondary" class="subtitle">
        粘贴 GitHub 公开仓库的 Pull Request 链接，提交后将进入后台分析。
        可同时提交多个，进度统一在右上角任务中心查看。
      </a-typography-paragraph>

      <a-form layout="vertical" @submit.prevent="onSubmit">
        <a-form-item
          label="PR 链接"
          :validate-status="errorMsg ? 'error' : ''"
          :help="errorMsg || undefined"
        >
          <a-input
            v-model:value="prUrl"
            size="large"
            placeholder="https://github.com/owner/repo/pull/123"
            allow-clear
            autofocus
          >
            <template #prefix>
              <GithubOutlined style="color: rgba(0,0,0,0.45)" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            block
          >
            加入分析队列
          </a-button>
        </a-form-item>
      </a-form>

      <a-alert
        type="info"
        show-icon
        banner
      >
        <template #message>
          <span>
            提交后立即返回首页，可继续提交下一条。点击右上角
            <UnorderedListOutlined /> <strong>任务</strong>
            查看进度与历史记录。
          </span>
        </template>
      </a-alert>
    </a-card>

    <div class="footer">
      <a-typography-text type="secondary">
        PRism · Enterprise Edition
      </a-typography-text>
    </div>
  </div>
</template>

<style scoped>
.home {
  min-height: calc(100vh - 56px - 48px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  background: #f0f2f5;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}
.logo {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: #1677ff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}
.brand-name {
  font-size: 20px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  line-height: 1.2;
}
.brand-desc {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  letter-spacing: 0.5px;
}
.card {
  width: 100%;
  max-width: 640px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03), 0 4px 16px rgba(0, 0, 0, 0.04);
  border-radius: 8px;
}
.title {
  margin-bottom: 4px !important;
}
.subtitle {
  margin-bottom: 24px !important;
}
.footer {
  margin-top: 28px;
  font-size: 12px;
}
</style>
