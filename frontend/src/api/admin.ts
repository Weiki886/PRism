import request from './request'

export interface AdminUser {
  id: number
  username: string
  email: string
  role: string
  createdAt: string
  updatedAt: string
}

export async function listUsers(): Promise<AdminUser[]> {
  const res = await request.get<AdminUser[]>('/api/admin/users')
  return res.data
}

export async function getUser(id: number): Promise<AdminUser> {
  const res = await request.get<AdminUser>(`/api/admin/users/${id}`)
  return res.data
}

export async function updateUserRole(id: number, role: string): Promise<void> {
  await request.put(`/api/admin/users/${id}/role`, null, { params: { role } })
}

export async function deleteUser(id: number): Promise<void> {
  await request.delete(`/api/admin/users/${id}`)
}
