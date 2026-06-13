/**
 * 资料 API
 */
import request from './request'

/** 获取资料分类 */
export function getCategories() {
  return request.get('/api/material/categories')
}

/** 获取课程列表（用于上传时选择关联课程） */
export function getCourseOptions() {
  return request.get('/api/course/list', { params: { page: 1, pageSize: 100 } })
}

/** 分页获取资料列表 */
export function getMaterialList(params) {
  return request.get('/api/material/list', { params })
}

/** 获取资料详情 */
export function getMaterialDetail(id) {
  return request.get(`/api/material/${id}`)
}

/** 下载资料文件（服务端负责授权和一次性扣分） */
export function downloadMaterialFile(id) {
  return request.get(`/api/material/${id}/download`, {
    responseType: 'blob',
    rawResponse: true
  })
}

/** 上传学习资料 */
export function uploadMaterial(data) {
  const form = new FormData()
  form.append('file', data.file)
  form.append('materialName', data.material_name)
  form.append('materialType', data.material_type)
  if (data.course_id != null) form.append('courseId', data.course_id)
  form.append('downloadScore', data.download_score ?? 0)
  if (data.keyword) form.append('keyword', data.keyword)
  return request.post('/api/material/upload', form)
}

export function saveBlobResponse(response, fallbackName = 'download') {
  const disposition = response.headers?.['content-disposition'] || ''
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const quoted = disposition.match(/filename="([^"]+)"/i)?.[1]
  let filename = fallbackName
  try {
    filename = encoded ? decodeURIComponent(encoded) : (quoted || fallbackName)
  } catch { filename = fallbackName }

  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** 举报违规资料 */
export function reportMaterial(id, data) {
  return request.post(`/api/material/${id}/report`, data)
}

/** 我的上传 */
export function getMyUploads() {
  return request.get('/api/material/my')
}

/** 重新提交已驳回的资料 */
export function resubmitMaterial(id) {
  return request.put(`/api/material/${id}/resubmit`)
}

/** 我的下载记录 */
export function getMyDownloads(params) {
  return request.get('/api/material/downloads', { params })
}
