import request from './request'
import type { PageResult } from './review'

export interface RepoInfo {
  fullName: string
  description: string | null
  language: string | null
  starCount: number
  forkCount: number
  openIssuesCount: number
  ownerAvatarUrl: string
  ownerName: string
  htmlUrl: string
  defaultBranch: string
  isPrivate: boolean
  topics: string[]
  pushedAt: string
}

export interface RepoPullRequest {
  number: number
  title: string
  author: string
  avatarUrl: string
  state: 'open' | 'closed'
  createdAt: string
  updatedAt: string
  labels: string[]
  htmlUrl: string
  merged: boolean
}

export interface RepoBrowsingRecord {
  id: number
  repoUrl: string
  fullName: string
  description: string
  language: string
  starCount: number
  ownerAvatarUrl: string
  htmlUrl: string
  isPrivate: boolean
  lastVisitedAt: string
}

export async function getRepoInfo(repoUrl: string): Promise<RepoInfo> {
  const res = await request.get<RepoInfo>('/api/repo/info', {
    params: { repoUrl },
  })
  return res.data
}

export async function getRepoPulls(
  repoUrl: string,
  page = 1,
  size = 20,
  state: 'open' | 'closed' | 'all' = 'all',
): Promise<PageResult<RepoPullRequest>> {
  const res = await request.get<PageResult<RepoPullRequest>>('/api/repo/pulls', {
    params: { repoUrl, page, size, state },
  })
  return res.data
}

export async function getRepoHistory(limit = 20): Promise<RepoBrowsingRecord[]> {
  const res = await request.get<RepoBrowsingRecord[]>('/api/repo/history', {
    params: { limit },
    silent: true,
  })
  return res.data
}

export async function deleteRepoHistory(id: number): Promise<void> {
  await request.delete(`/api/repo/history/${id}`)
}

export async function clearRepoHistory(): Promise<void> {
  await request.delete('/api/repo/history')
}
