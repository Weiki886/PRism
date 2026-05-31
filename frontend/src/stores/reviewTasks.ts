import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  createReview,
  deleteReview,
  getReview,
  getReviewHistory,
  retryReview,
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

  // 历史记录分页元数据（与后端 PageResult 对齐）
  const historyPage = ref(1)
  const historyPageSize = ref(10)
  const historyTotal = ref(0)
  const historyTotalPages = ref(0)

  // 搜索/筛选独立状态：不污染全局 tasks，避免影响顶栏徽章与进行中分区
  const searchKeyword = ref('')
  const statusFilter = ref<ReviewStatus | ''>('')
  const searchResults = ref<ReviewTask[]>([])
  const searchLoading = ref(false)
  const searchError = ref('')
  const isSearching = computed(
    () => searchKeyword.value.trim().length > 0 || statusFilter.value !== '',
  )

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

  /**
   * 调用后端 POST /api/review/{id}/retry 重新触发分析。
   * 仅在任务有 id 且状态为 completed/error 时有意义；后端会在 pending/processing 时返回 409。
   * 成功后将本地任务字段重置为 pending 并重启轮询。
   */
  async function retry(localId: string): Promise<void> {
    const task = findByLocalId(localId)
    if (!task || !task.id) return
    await retryReview(task.id)
    task.status = 'pending'
    task.submitError = ''
    task.riskCount = 0
    task.suggestionCount = 0
    task.finishedAt = null
    persist()
    startPolling(localId)
  }

  /**
   * 调用后端 DELETE /api/review/{id} 删除远端记录后再清本地。
   * 仅当任务有 id（已落库）时才请求；无 id 的本地草稿直接走 remove。
   * 抛出错误由调用方处理（用于显示提示与回滚 UI）。
   */
  async function deleteRemote(localId: string): Promise<void> {
    const task = findByLocalId(localId)
    if (!task) return
    if (!task.id) {
      remove(localId)
      return
    }
    await deleteReview(task.id)
    remove(localId)
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

  async function loadHistory(page = historyPage.value, size = historyPageSize.value): Promise<void> {
    historyLoading.value = true
    try {
      const result = await getReviewHistory(page, size)
      historyPage.value = result.page
      historyPageSize.value = result.size
      historyTotal.value = result.total
      historyTotalPages.value = result.totalPages

      // 替换 tasks 中"远端已完成"区域为当前页的记录；
      // 本地未提交成功（id 为 null）与进行中的任务一概保留，
      // 避免翻页时丢失本地最新提交或干扰轮询。
      const kept = tasks.value.filter((t) => !t.id || isInProgressStatus(t.status))
      const newRecords = result.records.map(reviewToTask)
      tasks.value = [...kept, ...newRecords]

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

  function changeHistoryPage(page: number) {
    if (page < 1) return
    if (historyTotalPages.value > 0 && page > historyTotalPages.value) return
    if (page === historyPage.value) return
    void loadHistory(page, historyPageSize.value)
  }

  function changeHistoryPageSize(size: number) {
    if (size === historyPageSize.value) return
    historyPageSize.value = size
    void loadHistory(1, size)
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

  async function searchHistory(): Promise<void> {
    const keyword = searchKeyword.value.trim()
    const status = statusFilter.value
    if (!keyword && !status) {
      searchResults.value = []
      searchError.value = ''
      return
    }
    searchLoading.value = true
    searchError.value = ''
    try {
      const result = await getReviewHistory(1, 50, keyword || undefined, status || undefined)
      searchResults.value = result.records.map(reviewToTask)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } }; message?: string }
      searchError.value = e?.response?.data?.message ?? e?.message ?? '搜索失败'
      searchResults.value = []
    } finally {
      searchLoading.value = false
    }
  }

  function resetSearch() {
    searchKeyword.value = ''
    statusFilter.value = ''
    searchResults.value = []
    searchError.value = ''
    searchLoading.value = false
  }

  return {
    tasks,
    inProgress,
    finished,
    inProgressCount,
    historyLoading,
    historyLoaded,
    historyPage,
    historyPageSize,
    historyTotal,
    historyTotalPages,
    searchKeyword,
    statusFilter,
    searchResults,
    searchLoading,
    searchError,
    isSearching,
    submit,
    ensurePolling,
    refreshOne,
    remove,
    deleteRemote,
    retry,
    clearFinished,
    resumeAll,
    loadHistory,
    changeHistoryPage,
    changeHistoryPageSize,
    searchHistory,
    resetSearch,
  }
})

function isInProgressStatus(s: ReviewStatus | 'submitting'): boolean {
  return s === 'submitting' || s === 'pending' || s === 'processing'
}
