/**
 * 通讯录 API
 */
import request from './request'

/** 获取联系人列表 */
export function getContactList(params) {
  return request.get('/api/contact/list', { params })
}

/** 搜索联系人（返回原始response，由调用方处理） */
export function searchContactRaw(keyword) {
  return request.get('/api/contact/search', { params: { keyword } })
}

/** 获取联系人详情 */
export function getContactDetail(id) {
  return request.get(`/api/contact/${id}`)
}

/** 获取自己的隐私设置 */
export function getMyAuth() {
  return request.get('/api/contact/auth/my')
}

/** 更新隐私设置 */
export function updateMyAuth(data) {
  return request.put('/api/contact/auth/update', data)
}

/** 获取我的好友列表 */
export function getMyFriends() {
  return request.get('/api/contact/my-friends')
}

/** 获取待处理的好友请求 */
export function getPendingRequests() {
  return request.get('/api/contact/pending')
}

/** 添加好友 */
export function addFriend(targetId) {
  return request.post(`/api/contact/add/${targetId}`)
}

/** 接受好友请求 */
export function acceptFriend(friendId) {
  return request.post(`/api/contact/accept/${friendId}`)
}

/** 删除好友 */
export function removeFriend(friendId) {
  return request.delete(`/api/contact/remove/${friendId}`)
}
