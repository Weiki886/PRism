<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { GithubOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { createReview, type ReviewResponse } from '@/api/review'

const emit = defineEmits<{
  (e: 'success', payload: ReviewResponse): void
}>()

const prUrl = ref('')
const loading = ref(false)
const errorMsg = ref('')

const PR_URL_RE = /^https:\/\/github\.com\/[^/]+\/[^/]+\/pull\/\d+/i

async function onSubmit() {
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
  loading.value = true
  try {
    const data = await createReview(url)
    message.success('分析完成')
    emit('success', data)
  } catch (err: unknown) {
    const e = err as { response?: { data?: { message?: string } }; message?: string }
    errorMsg.value =
      e?.response?.data?.message ??
      e?.message ??
      '请求失败，请确认后端服务已启动'
  } finally {
    loading.value = false
  }
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
        粘贴 GitHub 公开仓库的 Pull Request 链接，系统将自动抓取变更并生成审查报告。
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
            :disabled="loading"
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
            :loading="loading"
            block
          >
            {{ loading ? '正在分析，请稍候 (5~15s)…' : '开始分析' }}
          </a-button>
        </a-form-item>
      </a-form>

      <a-alert
        v-if="loading"
        type="info"
        show-icon
        message="AI 正在阅读代码变更"
        description="分析过程通常需要 5~15 秒，请勿关闭页面。"
      />
      <a-alert
        v-else
        type="info"
        show-icon
        banner
        message="支持公开仓库的 PR；首次分析请确保后端运行在 :8080。"
      />
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
  min-height: 100vh;
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
