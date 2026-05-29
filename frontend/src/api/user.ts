import request from './request'

export interface UpdateProfileRequest {
  email: string
}

export interface UpdatePasswordRequest {
  oldPassword: string
  newPassword: string
}

export async function updateProfile(payload: UpdateProfileRequest): Promise<void> {
  await request.put('/api/user/profile', payload)
}

export async function updatePassword(payload: UpdatePasswordRequest): Promise<void> {
  await request.put('/api/user/password', payload)
}
