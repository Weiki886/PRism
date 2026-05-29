import request from './request'

export type FeedbackType = 'FALSE_POSITIVE' | 'CONFIRMED'

export interface RiskFeedbackRequest {
  riskIndex: number
  feedback: FeedbackType
  comment?: string
}

export interface RiskFeedbackStat {
  riskIndex: number
  falsePositiveCount: number
  confirmedCount: number
  myFeedback: FeedbackType | null
}

export async function submitFeedback(
  reviewId: string,
  payload: RiskFeedbackRequest,
): Promise<void> {
  await request.post(`/api/review/${reviewId}/feedback`, payload)
}

export async function getFeedbackStats(reviewId: string): Promise<RiskFeedbackStat[]> {
  const res = await request.get<RiskFeedbackStat[]>(`/api/review/${reviewId}/feedback`)
  return res.data
}
