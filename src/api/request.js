/**
 * Axios 实例 + 请求/响应拦截器
 *
 * 职责：
 * - 请求拦截：自动附加 Authorization Token
 * - 响应拦截：统一错误处理、Token 刷新、消息提示
 * - 严禁向前端暴露后端代码异常
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getAccessToken, setAccessToken, clearTokens, isTokenExpired, getRefreshToken } from '@/utils/auth'
import router from '@/router'

// 使用 vite 环境变量
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const TIMEOUT = Number(import.meta.env.VITE_REQUEST_TIMEOUT) || 10000

const service = axios.create({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

/** 是否正在刷新 Token */
let isRefreshing = false
/** 等待 Token 刷新的请求队列 */
let pendingRequests = []

/**
 * 处理队列中的请求
 */
function processPendingRequests(error = null) {
  pendingRequests.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error)
    } else {
      resolve()
    }
  })
  pendingRequests = []
}

/**
 * 刷新 Access Token
 */
async function refreshToken() {
  const refreshTokenValue = getRefreshToken()
  if (!refreshTokenValue) {
    throw new Error('No refresh token')
  }
  const res = await axios.post(`${BASE_URL}/api/auth/refresh`, {
    refreshToken: refreshTokenValue
  })
  if (res.data.code === 200) {
    const { accessToken, refreshToken: newRefreshToken } = res.data.data
    setAccessToken(accessToken)
    if (newRefreshToken) {
      localStorage.setItem('refresh_token', newRefreshToken)
    }
    return accessToken
  }
  throw new Error('Refresh failed')
}

// ======================
// 请求拦截器
// ======================
service.interceptors.request.use(
  (config) => {
    const token = getAccessToken()
    if (token) {
      // 如果 Token 即将过期，先尝试刷新
      if (isTokenExpired(token) && config.url !== '/api/auth/refresh') {
        // 将请求加入等待队列
        return new Promise((resolve) => {
          pendingRequests.push({
            resolve: () => {
              config.headers.Authorization = `Bearer ${getAccessToken()}`
              resolve(config)
            },
            reject: (err) => {
              resolve(config) // 即使刷新失败也继续请求，由响应拦截器处理 401
            }
          })
          // 触发刷新
          if (!isRefreshing) {
            isRefreshing = true
            refreshToken()
              .then(() => processPendingRequests())
              .catch((err) => processPendingRequests(err))
              .finally(() => { isRefreshing = false })
          }
        })
      }
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// ======================
// 响应拦截器
// ======================
service.interceptors.response.use(
  (response) => {
    const res = response.data

    // 统一响应格式: { code, message, data }
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  async (error) => {
    if (error.code === 'ECONNABORTED' || error.message.includes('timeout')) {
      ElMessage.error('网络请求超时，请稍后重试')
      return Promise.reject(error)
    }

    if (!error.response) {
      ElMessage.error('网络异常，请稍后重试')
      return Promise.reject(error)
    }

    const { status, data } = error.response

    switch (status) {
      case 400:
        ElMessage.error(data?.message || '请求参数错误')
        break

      case 401: {
        // 尝试刷新 Token
        const token = getAccessToken()
        if (token && !isRefreshing) {
          isRefreshing = true
          try {
            const newToken = await refreshToken()
            isRefreshing = false
            // 重试原请求
            error.config.headers.Authorization = `Bearer ${newToken}`
            return service(error.config)
          } catch {
            isRefreshing = false
            // 刷新失败，跳转登录
            ElMessage.error('登录已过期，请重新登录')
            clearTokens()
            router.push('/login')
          }
        } else {
          ElMessage.error('请先登录')
          clearTokens()
          router.push('/login')
        }
        break
      }

      case 403:
        ElMessage.error('权限不足，无法执行此操作')
        break

      case 404:
        ElMessage.error(data?.message || '请求的资源不存在')
        break

      case 500:
        // 【关键】不暴露后端异常，统一转为友好提示
        ElMessage.error('网络异常，请稍后重试')
        break

      default:
        ElMessage.error(data?.message || '网络异常，请稍后重试')
    }

    return Promise.reject(error)
  }
)

export default service
