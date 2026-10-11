import axios from 'axios'
import { clearAllAuth, getAccessToken, getRefreshToken, getAuthSessionId, isTokenExpired, saveTokens } from './auth'
import { mockAdapter } from '../api/mock'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''
const publicAuthPaths = new Set([
  '/auth/login', '/auth/login/sms', '/auth/sms/send', '/auth/register', '/auth/refresh',
  '/auth/password-reset/code', '/auth/password-reset'
])

export function normalizeApiUrl(url) {
  return BASE_URL === '/api' && String(url).startsWith('/api/') ? url.slice(4) : url
}

export function isPublicAuthRequest(url) {
  const path = String(url || '').split(/[?#]/)[0].replace(/^\/api(?=\/)/, '')
  return publicAuthPaths.has(path)
}

// Refresh uses a separate client so it cannot enter the business client's interceptors.
const refreshClient = axios.create({
  baseURL: BASE_URL,
  timeout: Number(import.meta.env.VITE_REQUEST_TIMEOUT) || 10000,
  adapter: import.meta.env.VITE_USE_MOCK === 'true' ? mockAdapter : undefined
})

let refreshPromise = null
let refreshIdentity = null

export function sessionChangedError() {
  return Object.assign(new Error('登录状态已经改变'), { sessionChanged: true })
}

export function isSessionRejected(error) {
  return error.sessionRejected === true || [400, 401, 403].includes(error.response?.status)
}

export function ensureFreshAccessToken() {
  const identity = getAuthSessionId()
  if (!refreshPromise || refreshIdentity !== identity) {
    const pending = refreshAccessToken().finally(() => {
      if (refreshPromise === pending) refreshPromise = null
    })
    refreshPromise = pending
    refreshIdentity = identity
  }
  return refreshPromise
}

async function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  const identity = getAuthSessionId()
  if (!refreshToken) throw Object.assign(new Error('缺少登录凭证，请重新登录'), { sessionRejected: true })
  let response
  try {
    response = await refreshClient.post(normalizeApiUrl('/api/auth/refresh'), { refreshToken })
  } catch (error) {
    if (getAuthSessionId() !== identity || getRefreshToken() !== refreshToken) throw sessionChangedError()
    throw error
  }
  if (getAuthSessionId() !== identity || getRefreshToken() !== refreshToken) throw sessionChangedError()
  const result = response.data
  if (result?.code !== 200 || !result.data?.accessToken || !result.data?.refreshToken) {
    throw Object.assign(new Error(result?.message || '登录续期暂时失败，请稍后重试'), { sessionRejected: [400, 401, 403].includes(result?.code) })
  }
  const tokens = result.data
  saveTokens(tokens.accessToken, tokens.refreshToken)
  const { useUserStore } = await import('@/stores/user')
  const store = useUserStore()
  // A logout or a new login may have happened while the module was loading.
  if (getAccessToken() === tokens.accessToken) {
    store.accessToken = tokens.accessToken
    store.refreshToken = tokens.refreshToken
  }
  return tokens.accessToken
}

export async function ensureSession() {
  const accessToken = getAccessToken()
  if (accessToken && !isTokenExpired(accessToken)) return true
  const refreshToken = getRefreshToken()
  if (!refreshToken || isTokenExpired(refreshToken, 0)) {
    clearAllAuth()
    return false
  }
  try {
    await ensureFreshAccessToken()
    return true
  } catch (error) {
    if (error.sessionChanged) return !!getAccessToken() && !isTokenExpired(getAccessToken())
    if (isSessionRejected(error)) { clearAllAuth(); return false }
    throw error
  }
}
