import axios from 'axios'
import { ElMessage } from 'element-plus'
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  isTokenExpired,
  saveTokens
} from '@/utils/auth'
import router from '@/router'
import { mockAdapter } from './mock'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''
const TIMEOUT = Number(import.meta.env.VITE_REQUEST_TIMEOUT) || 10000
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

const service = axios.create({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  adapter: USE_MOCK ? mockAdapter : undefined
})

let refreshPromise = null

async function requestNewAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new Error('No refresh token')

  const response = await service.post('/api/auth/refresh', { refreshToken })
  const tokens = response.data || {}
  saveTokens(tokens.accessToken, tokens.refreshToken || refreshToken)
  // Sync to pinia user-store so persist doesn't rehydrate stale tokens
  import('@/stores/user').then(({ useUserStore }) => {
    const store = useUserStore()
    if (store.accessToken !== tokens.accessToken) store.accessToken = tokens.accessToken
    if (tokens.refreshToken && store.refreshToken !== tokens.refreshToken) store.refreshToken = tokens.refreshToken
  }).catch(() => {})
  return tokens.accessToken
}

function ensureFreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = requestNewAccessToken().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

function expireSession(message = '登录已过期，请重新登录') {
  clearTokens()
  import('@/stores/user').then(({ useUserStore }) => {
    useUserStore().logout()
  }).catch(() => {})
  ElMessage.error(message)
  if (router.currentRoute.value.path !== '/login') router.push('/login')
}

service.interceptors.request.use(async config => {
  if (config.data instanceof FormData) {
    config.headers?.delete?.('Content-Type')
    delete config.headers?.['Content-Type']
  }

  let token = getAccessToken()
  if (token && isTokenExpired(token) && config.url !== '/api/auth/refresh') {
    try {
      token = await ensureFreshAccessToken()
    } catch (error) {
      expireSession()
      return Promise.reject(error)
    }
  }
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

service.interceptors.response.use(
  response => {
    if (response.config.rawResponse) return response

    const result = response.data
    if (result?.code !== 200) {
      ElMessage.error(result?.message || '请求失败')
      return Promise.reject(new Error(result?.message || '请求失败'))
    }
    return result
  },
  async error => {
    if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      ElMessage.error('网络请求超时，请稍后重试')
      return Promise.reject(error)
    }
    if (!error.response) {
      ElMessage.error('网络异常，请稍后重试')
      return Promise.reject(error)
    }

    const { status, data } = error.response
    const original = error.config || {}
    if (status === 401 && !original._retry && getRefreshToken()
        && original.url !== '/api/auth/refresh') {
      original._retry = true
      try {
        const token = await ensureFreshAccessToken()
        original.headers.Authorization = `Bearer ${token}`
        return service(original)
      } catch (refreshError) {
        expireSession()
        return Promise.reject(refreshError)
      }
    }

    if (status === 401) expireSession('请先登录')
    else if (status === 403) ElMessage.error('权限不足，无法执行此操作')
    else if (status === 404) ElMessage.error(data?.message || '请求的资源不存在')
    else ElMessage.error(data?.message || '请求失败，请稍后重试')
    return Promise.reject(error)
  }
)

export default service
