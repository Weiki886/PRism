import request from './request'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  role: string
}

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const res = await request.post<LoginResponse>('/api/auth/login', payload)
  return res.data
}

export async function register(payload: RegisterRequest): Promise<void> {
  await request.post('/api/auth/register', payload)
}

export async function getGithubAuthorizeUrl(): Promise<{ authorizeUrl: string }> {
  const res = await request.get<{ authorizeUrl: string }>('/api/auth/github')
  return res.data
}

export async function githubCallback(code: string): Promise<LoginResponse> {
  const res = await request.post<LoginResponse>('/api/auth/github/callback', { code })
  return res.data
}
