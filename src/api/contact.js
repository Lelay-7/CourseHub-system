/**
 * 通讯录 API
 */
import request from './request'

/** 获取通讯录列表 */
export function getContactList(params) {
  return request.get('/api/contact/list', { params })
}

/** 搜索联系人 */
export function searchContact(params) {
  return request.get('/api/contact/search', { params })
}

/** 获取联系人详情 */
export function getContactDetail(id) {
  return request.get(`/api/contact/${id}`)
}
