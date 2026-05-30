import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  createReview,
  getReview,
  getReviewHistory,
  type ReviewResponse,
  type ReviewStatus,
} from '@/api/review'

export interface ReviewTask {
  id: string | null
  localId: string
  prUrl: string
  status: ReviewStatus | 'submitting'
  submitError: string
  prTitle: string
  author: string
  riskCount: number
  suggestionCount: number
  createdAt: number
  finishedAt: number | null
}

const STORAGE_KEY = 'prism_review_tasks'
const POLL_INTERVAL = 2000
const MAX_TASKS = 50

function loadFromStorage(): ReviewTask[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as ReviewTask[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function saveToStorage(list: ReviewTask[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list.slice(0, MAX_TASKS)))
  } catch {
    // 忽略配额错误
  }
}

function makeLocalId(): string {
  return `local_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

export const useReviewTaskStore = defineStore('reviewTasks', () => {
  const tasks = ref<ReviewTask[]>(loadFromStorage())
  const historyLoading = ref(false)
  const historyLoaded = ref(false)
  const pollTimers = new Map<string, ReturnType<typeof setTimeout>>()

  const inProgress = computed(() =>
    tasks.value.filter((t) => isInProgressStatus(t.status)),
  )
  const finished = computed(() =>
    tasks.value.filter((t) => !isInProgressStatus(t.status)),
  )
  const inProgressCount = computed(() => inProgress.value.length)

  function persist() {
    saveToStorage(tasks.value)
  }

  function findByLocalId(localId: string) {
    return tasks.value.find((t) => t.localId === localId)
  }

  function findById(id: string) {
    return tasks.value.find((t) => t.id === id)
  }

  function upsertFromReview(task: ReviewTask, data: ReviewResponse) {
    task.id = data.id
    task.status = data.status
    task.prTitle = data.prTitle || task.prTitle
    task.author = data.author || task.author
    task.riskCount = data.risks?.length ?? 0
    task.suggestionCount = data.suggestions?.length ?? 0
    if (data.status === 'completed' || data.status === 'error') {
      task.finishedAt = Date.now()
    }
    persist()
  }

  async function submit(prUrl: string): Promise<ReviewTask> {
    const task: ReviewTask = {
      id: null,
      localId: makeLocalId(),
      prUrl,
      status: 'submitting',
      submitError: '',
      prTitle: '',
      author: '',
      riskCount: 0,
      suggestionCount: 0,
      createdAt: Date.now(),
      finishedAt: null,
    }
    tasks.value.unshift(task)
    persist()

    try {
      const data = await createReview(prUrl)
      task.id = data.id
      task.status = data.status === 'completed' || data.status === 'error' ? data.status : 'pending'
      task.prTitle = data.prTitle || ''
      task.author = data.author || ''
      task.riskCount = data.risks?.length ?? 0
      task.suggestionCount = data.suggestions?.length ?? 0
      persist()
      startPolling(task.localId)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } }; message?: string }
      task.status = 'error'
      task.submitError =
        e?.response?.data?.message ??
        e?.message ??
        '提交失败，请确认后端服务已启动'
      task.finishedAt = Date.now()
      persist()
    }
    return task
  }

  function startPolling(localId: string) {
    stopPolling(localId)
    const tick = async () => {
      const task = findByLocalId(localId)
      if (!task || !task.id) return
      try {
        const data = await getReview(task.id)
        upsertFromReview(task, data)
        if (data.status === 'completed' || data.status === 'error') {
          stopPolling(localId)
          return
        }
      } catch {
        // 静默重试
      }
      const timer = setTimeout(tick, POLL_INTERVAL)
      pollTimers.set(localId, timer)
    }
    const timer = setTimeout(tick, POLL_INTERVAL)
    pollTimers.set(localId, timer)
  }

  function stopPolling(localId: string) {
    const timer = pollTimers.get(localId)
    if (timer) {
      clearTimeout(timer)
      pollTimers.delete(localId)
    }
  }

  function ensurePolling(taskOrId: ReviewTask | string) {
    const task = typeof taskOrId === 'string' ? findById(taskOrId) : taskOrId
    if (!task || !task.id) return
    if (!isInProgressStatus(task.status)) return
    if (pollTimers.has(task.localId)) return
    startPolling(task.localId)
  }

  async function refreshOne(id: string): Promise<ReviewResponse | null> {
    try {
      const data = await getReview(id)
      let task = findById(id)
      if (!task) {
        task = {
          id: data.id,
          localId: makeLocalId(),
          prUrl: '',
          status: data.status,
          submitError: '',
          prTitle: data.prTitle,
          author: data.author,
          riskCount: data.risks?.length ?? 0,
          suggestionCount: data.suggestions?.length ?? 0,
          createdAt: Date.now(),
          finishedAt:
            data.status === 'completed' || data.status === 'error' ? Date.now() : null,
        }
        tasks.value.push(task)
      } else {
        upsertFromReview(task, data)
      }
      persist()
      ensurePolling(task)
      return data
    } catch {
      return null
    }
  }

  function remove(localId: string) {
    stopPolling(localId)
    tasks.value = tasks.value.filter((t) => t.localId !== localId)
    persist()
  }

  function clearFinished() {
    tasks.value = tasks.value.filter((t) => isInProgressStatus(t.status))
    persist()
  }

  function resumeAll() {
    for (const t of tasks.value) {
      if (t.id && isInProgressStatus(t.status)) {
        startPolling(t.localId)
      }
    }
  }

  async function loadHistory(): Promise<void> {
    historyLoading.value = true
    try {
      // 后端按用户分页返回，这里拉前 50 条作为历史展示
      const list = await getReviewHistory(1, 50)
      const known = new Set(tasks.value.map((t) => t.id).filter(Boolean) as string[])
      const merged: ReviewTask[] = []
      for (const r of list) {
        if (known.has(r.id)) continue
        merged.push(reviewToTask(r))
      }
      // 远端记录追加在本地任务之后；本地新提交的保持在最前
      tasks.value = [...tasks.value, ...merged].slice(0, MAX_TASKS)
      // 如果远端返回里有进行中的（理论上少见），交给轮询接管
      for (const t of tasks.value) {
        if (t.id && isInProgressStatus(t.status) && !pollTimers.has(t.localId)) {
          startPolling(t.localId)
        }
      }
      persist()
      historyLoaded.value = true
    } finally {
      historyLoading.value = false
    }
  }

  function reviewToTask(r: ReviewResponse): ReviewTask {
    const finished = r.status === 'completed' || r.status === 'error'
    return {
      id: r.id,
      localId: makeLocalId(),
      prUrl: '',
      status: r.status,
      submitError: '',
      prTitle: r.prTitle || '',
      author: r.author || '',
      riskCount: r.risks?.length ?? 0,
      suggestionCount: r.suggestions?.length ?? 0,
      createdAt: 0,
      finishedAt: finished ? 0 : null,
    }
  }

  return {
    tasks,
    inProgress,
    finished,
    inProgressCount,
    historyLoading,
    historyLoaded,
    submit,
    ensurePolling,
    refreshOne,
    remove,
    clearFinished,
    resumeAll,
    loadHistory,
  }
})

function isInProgressStatus(s: ReviewStatus | 'submitting'): boolean {
  return s === 'submitting' || s === 'pending' || s === 'processing'
}
