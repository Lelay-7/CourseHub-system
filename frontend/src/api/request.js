import axios from 'axios'
import { ElMessage } from 'element-plus'
import {
  clearAllAuth,
  getAccessToken,
  getRefreshToken,
  getAuthSessionId,
  isTokenExpired
} from '@/utils/auth'
import { ensureFreshAccessToken, isPublicAuthRequest, normalizeApiUrl, sessionChangedError } from '@/utils/session'
export { ensureSession } from '@/utils/session'
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

let sessionExpired = false

function expireSession(message = '登录已过期，请重新登录') {
  if (sessionExpired) return
  sessionExpired = true
  clearAllAuth()
  import('@/stores/user').then(({ useUserStore }) => {
    if (!getAccessToken() && !getRefreshToken()) useUserStore().logout()
  }).catch(() => {})
  ElMessage.error(message)
  if (router.currentRoute.value.path !== '/login') router.push('/login')
  // 延迟重置标记，避免短时间内重复触发
  setTimeout(() => { sessionExpired = false }, 2000)
}

service.interceptors.request.use(async config => {
  config.url = normalizeApiUrl(config.url)

  if (config.data instanceof FormData) {
    config.headers?.delete?.('Content-Type')
    delete config.headers?.['Content-Type']
  }

  if (isPublicAuthRequest(config.url)) {
    delete config.headers.Authorization
    return config
  }
  if (!Object.hasOwn(config, '_sessionIdentity')) config._sessionIdentity = getAuthSessionId()
  if (config._sessionIdentity !== getAuthSessionId()) throw sessionChangedError()
  let token = getAccessToken()
  if ((!token || isTokenExpired(token)) && getRefreshToken()) {
    try {
      token = await ensureFreshAccessToken()
      if (config._sessionIdentity !== getAuthSessionId()) throw sessionChangedError()
    } catch (error) {
      if (!error.sessionChanged) expireSession()
      error.userNotified = true
      return Promise.reject(error)
    }
  }
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

service.interceptors.response.use(
  response => {
    if (!isPublicAuthRequest(response.config.url)
        && response.config._sessionIdentity !== getAuthSessionId()) throw sessionChangedError()
    if (response.config.rawResponse) return response

    const result = response.data
    if (result?.code !== 200) {
      ElMessage.error(result?.message || '请求失败')
      return Promise.reject(Object.assign(new Error(result?.message || '请求失败'), { userNotified: true }))
    }
    return result
  },
  async error => {
    if (error.userNotified || error.sessionChanged) return Promise.reject(error)
    if (error.config && !isPublicAuthRequest(error.config.url)
        && error.config._sessionIdentity !== getAuthSessionId()) return Promise.reject(sessionChangedError())
    error.userNotified = true
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
    if (isPublicAuthRequest(original.url)) {
      ElMessage.error(data?.message || '登录请求失败，请重试')
      return Promise.reject(error)
    }
    if (status === 401 && !original._retry && getRefreshToken()) {
      original._retry = true
      try {
        const token = await ensureFreshAccessToken()
        original.headers.Authorization = `Bearer ${token}`
        return service(original)
      } catch (refreshError) {
        if (!refreshError.sessionChanged) expireSession()
        refreshError.userNotified = true
        return Promise.reject(refreshError)
      }
    }

    if (status === 401) expireSession('登录已失效，请重新登录')
    else if (status === 403) ElMessage.error('权限不足，无法执行此操作')
    else if (status === 404) ElMessage.error(data?.message || '请求的资源不存在')
    else ElMessage.error(data?.message || '请求失败，请稍后重试')
    return Promise.reject(error)
  }
)

export default service
