/**
 * Token 管理工具
 * 职责：Access Token / Refresh Token 的存取、过期判断、清除
 */

const ACCESS_TOKEN_KEY = 'access_token'
const REFRESH_TOKEN_KEY = 'refresh_token'

/**
 * 获取 Access Token
 */
export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

/**
 * 设置 Access Token
 */
export function setAccessToken(token) {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

/**
 * 获取 Refresh Token
 */
export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

/**
 * 设置 Refresh Token
 */
export function setRefreshToken(token) {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

/**
 * 保存完整的 Token 对
 */
export function saveTokens(accessToken, refreshToken) {
  setAccessToken(accessToken)
  setRefreshToken(refreshToken)
}

/**
 * 判断 Token 是否已过期（本地 JWT 解码）
 * @param {string} token - JWT token
 * @returns {boolean}
 */
export function isTokenExpired(token) {
  if (!token) return true
  try {
    const payload = JSON.parse(
      decodeURIComponent(
        atob(token.split('.')[1])
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      )
    )
    const now = Math.floor(Date.now() / 1000)
    // 提前 5 分钟视为过期，避免在请求中过期
    return payload.exp && payload.exp < now + 300
  } catch {
    return true
  }
}

/**
 * 清除所有 Token
 */
export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/**
 * 判断是否已登录（有有效 Token）
 */
export function isAuthenticated() {
  const token = getAccessToken()
  return token && !isTokenExpired(token)
}

/**
 * 获取当前用户类型（从 localStorage 的 user_info 解析）
 * @returns {number|null} 1-学生 2-教师 3-管理员，无信息时返回 null
 */
export function getUserType() {
  try {
    const raw = localStorage.getItem('user_info')
    if (raw) {
      const info = JSON.parse(raw)
      return info.userType || info.user_type || null
    }
  } catch { /* ignore */ }
  return null
}

/**
 * 彻底清除所有登录态（token + user_info + pinia user-store）
 */
export function clearAllAuth() {
  clearTokens()
  localStorage.removeItem('user_info')
  localStorage.removeItem('user-store')
  // 清除所有以 pinia 持久化 key 开头的内容
  Object.keys(localStorage).forEach(key => {
    if (key.startsWith('user-store') || key.startsWith('user_store')) {
      localStorage.removeItem(key)
    }
  })
}
