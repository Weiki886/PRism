import request from './request'

export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export interface RiskItem {
  level: RiskLevel
  file: string
  line: number | null
  description: string
  confidence: ConfidenceLevel
}

export type ReviewStatus = 'pending' | 'processing' | 'completed' | 'error'

export interface ReviewResponse {
  id: string
  prTitle: string
  author: string
  summary: string
  risks: RiskItem[]
  suggestions: string[]
  status: ReviewStatus
}

export async function createReview(prUrl: string): Promise<ReviewResponse> {
  const res = await request.post<ReviewResponse>('/api/review', { prUrl })
  return res.data
}

export async function getReview(id: string): Promise<ReviewResponse> {
  const res = await request.get<ReviewResponse>(`/api/review/${id}`)
  return res.data
}

export async function getReviewHistory(page = 1, size = 10): Promise<ReviewResponse[]> {
  const res = await request.get<ReviewResponse[]>('/api/review/history', {
    params: { page, size },
  })
  return res.data
}
