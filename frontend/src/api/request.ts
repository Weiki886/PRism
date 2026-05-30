import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

declare module 'axios' {
  export interface InternalAxiosRequestConfig {
    /** 设为 true 时跳过响应拦截器的全局错误 toast，由调用方自己处理。 */
    silent?: boolean
  }
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 60_000,
})

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('prism_token')
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (res: AxiosResponse<ApiResult<unknown>>) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) {
        return { ...res, data: body.data } as AxiosResponse
      }
      if (!res.config.silent) {
        message.error(body.message || '请求失败')
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return res
  },
  (err: AxiosError<ApiResult<unknown>>) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || err.message || '网络错误'
    const silent = err.config?.silent
    if (status === 401) {
      localStorage.removeItem('prism_token')
      message.error('登录已过期，请重新登录')
      const path = window.location.pathname
      if (!path.startsWith('/login') && !path.startsWith('/register')) {
        window.location.href = '/login'
      }
    } else if (status === 403) {
      if (!silent) message.error('权限不足')
    } else {
      if (!silent) message.error(msg)
    }
    return Promise.reject(err)
  },
)

export default request
