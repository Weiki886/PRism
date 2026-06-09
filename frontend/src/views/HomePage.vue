<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { GithubOutlined, ThunderboltOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import { useReviewTaskStore } from '@/stores/reviewTasks'

const taskStore = useReviewTaskStore()
const router = useRouter()

const prUrl = ref('')
const errorMsg = ref('')

const PR_URL_RE = /^https:\/\/github\.com\/[^/]+\/[^/]+\/pull\/\d+/i

/**
 * 从用户输入中提取仓库链接。
 * 支持：标准链接、.git 后缀、git clone 命令、/tree/...、/blob/... 等子页面
 */
function extractRepoUrl(input: string): string | null {
  let url = input.trim()
  // 去除 "git clone" 前缀
  url = url.replace(/^git\s+clone\s+/i, '').trim()
  // 匹配 GitHub 仓库链接，提取 owner/repo
  const m = url.match(/^https?:\/\/github\.com\/([^/]+)\/([^/\s?#]+)/i)
  if (!m) return null
  // 去除 .git 后缀和子路径
  const repo = m[2].replace(/\.git$/, '')
  return `https://github.com/${m[1]}/${repo}`
}

function onSubmit() {
  errorMsg.value = ''
  const url = prUrl.value.trim()
  if (!url) {
    errorMsg.value = '请输入 PR 链接或仓库链接'
    return
  }
  if (PR_URL_RE.test(url)) {
    // PR 链接 → 提交分析（严格匹配，精确到 pull/number）
    void taskStore.submit(url)
    message.success({
      content: '已加入分析队列，可在右上角"任务"中查看进度',
      duration: 3,
    })
    prUrl.value = ''
  } else {
    const repoUrl = extractRepoUrl(url)
    if (repoUrl) {
      // 仓库链接 → 跳转仓库浏览页
      router.push({ name: 'repo-browser', query: { url: repoUrl } })
      prUrl.value = ''
    } else {
      errorMsg.value = '无法识别该链接。支持 GitHub PR 链接（如 .../pull/123）或仓库链接（如 github.com/owner/repo）'
    }
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
        开始 AI 代码审查 / 浏览仓库
      </a-typography-title>
      <a-typography-paragraph type="secondary" class="subtitle">
        输入 GitHub PR 链接直接提交分析，或输入仓库链接浏览 PR 列表。
        可同时提交多个 PR 分析，进度统一在右上角任务中心查看。
      </a-typography-paragraph>

      <a-form layout="vertical" @submit.prevent="onSubmit">
        <a-form-item
          label="GitHub 链接"
          :validate-status="errorMsg ? 'error' : ''"
          :help="errorMsg || undefined"
        >
          <a-input
            v-model:value="prUrl"
            size="large"
            placeholder="https://github.com/owner/repo/pull/123 或 https://github.com/owner/repo"
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
