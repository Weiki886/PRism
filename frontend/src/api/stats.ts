import request from './request'
import type { RiskLevel } from './review'

export interface ReviewStats {
  totalReviews: number
  completedReviews: number
  errorReviews: number
  processingReviews: number
  totalRisks: number
  riskLevelDistribution: Partial<Record<RiskLevel, number>>
}

export interface FeedbackOverview {
  totalFeedbacks: number
  falsePositiveCount: number
  confirmedCount: number
  falsePositiveRate: number
}

export async function getReviewStats(): Promise<ReviewStats> {
  const res = await request.get<ReviewStats>('/api/review/stats', { silent: true })
  return res.data
}

export async function getFeedbackOverview(): Promise<FeedbackOverview> {
  const res = await request.get<FeedbackOverview>('/api/feedback-overview', { silent: true })
  return res.data
}
