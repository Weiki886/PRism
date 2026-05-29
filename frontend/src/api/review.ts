import axios from 'axios'

export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'

export interface RiskItem {
  level: RiskLevel
  file: string
  line: number | null
  description: string
}

export interface ReviewResponse {
  id: string
  prTitle: string
  author: string
  summary: string
  risks: RiskItem[]
  suggestions: string[]
  status: 'completed' | 'error'
}

const http = axios.create({
  timeout: 60_000,
})

export async function createReview(prUrl: string): Promise<ReviewResponse> {
  const res = await http.post<ReviewResponse>('/api/review', { prUrl })
  return res.data
}

export async function getReview(id: string): Promise<ReviewResponse> {
  const res = await http.get<ReviewResponse>(`/api/review/${id}`)
  return res.data
}
