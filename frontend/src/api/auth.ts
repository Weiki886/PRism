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
