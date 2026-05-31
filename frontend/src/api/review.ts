import request from './request'

export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW'
export type RiskSource = 'AI' | 'RULE'

export interface RiskItem {
  level: RiskLevel
  file: string
  line: number | null
  description: string
  confidence: ConfidenceLevel
  suggestedFix?: string | null
  source?: RiskSource
}

export type ReviewStatus = 'pending' | 'processing' | 'completed' | 'error'

export type MergeAdvice = 'RECOMMEND' | 'CAUTION' | 'NOT_RECOMMEND'

export interface ReviewResponse {
  id: string
  prTitle: string
  author: string
  summary: string
  risks: RiskItem[]
  suggestions: string[]
  status: ReviewStatus
  healthScore?: number | null
  mergeAdvice?: MergeAdvice | null
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export async function createReview(prUrl: string): Promise<ReviewResponse> {
  const res = await request.post<ReviewResponse>('/api/review', { prUrl })
  return res.data
}

export async function getReview(id: string): Promise<ReviewResponse> {
  const res = await request.get<ReviewResponse>(`/api/review/${id}`)
  return res.data
}

export async function getReviewHistory(
  page = 1,
  size = 10,
  keyword?: string,
  status?: ReviewStatus | '',
): Promise<PageResult<ReviewResponse>> {
  const params: Record<string, string | number> = { page, size }
  if (keyword) params.keyword = keyword
  if (status) params.status = status
  const res = await request.get<PageResult<ReviewResponse>>('/api/review/history', {
    params,
  })
  return res.data
}

export async function deleteReview(id: string): Promise<void> {
  await request.delete(`/api/review/${id}`)
}

export async function retryReview(id: string): Promise<ReviewResponse> {
  const res = await request.post<ReviewResponse>(`/api/review/${id}/retry`)
  return res.data
}
