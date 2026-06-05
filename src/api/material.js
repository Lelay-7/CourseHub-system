/**
 * 资料 API
 */
import request from './request'

/** 获取资料分类 */
export function getCategories() {
  return request.get('/api/material/categories')
}

/** 分页获取资料列表 */
export function getMaterialList(params) {
  return request.get('/api/material/list', { params })
}

/** 获取资料详情 */
export function getMaterialDetail(id) {
  return request.get(`/api/material/${id}`)
}

/** 获取资料下载链接（扣除积分） */
export function getDownloadUrl(id) {
  return request.get(`/api/material/${id}/download`)
}
