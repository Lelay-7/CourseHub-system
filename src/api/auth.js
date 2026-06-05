/**
 * 认证 API
 * - 账号密码登录
 * - 手机验证码登录
 * - 发送验证码
 * - 刷新 Token
 * - 获取用户信息
 */
import request from './request'

/** 账号密码登录 */
export function loginByPassword(data) {
  return request.post('/api/auth/login', data)
}

/** 手机验证码登录 */
export function loginBySms(data) {
  return request.post('/api/auth/login/sms', data)
}

/** 发送短信验证码 */
export function sendSmsCode(phone) {
  return request.post('/api/auth/sms/send', { phone })
}

/** 刷新 Token */
export function refreshToken(refreshToken) {
  return request.post('/api/auth/refresh', { refreshToken })
}

/** 获取当前用户信息 */
export function getUserInfo() {
  return request.get('/api/auth/userinfo')
}
