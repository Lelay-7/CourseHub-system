import request from './request'

/** 提交反馈 */
export function submitFeedback(data) {
  return request.post('/api/feedback', data)
}

/** 获取我的反馈 */
export function getMyFeedback() {
  return request.get('/api/feedback/my')
}
