import request from './request'

/** 更新个人资料 */
export function updateProfile(data) {
  return request.put('/api/user/profile', data)
}

/** 上传头像 */
export function uploadAvatar(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/api/user/avatar', form)
}
