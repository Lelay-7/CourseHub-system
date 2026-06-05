/**
 * 课程 API
 */
import request from './request'

/** 获取课程分类 */
export function getCategories() {
  return request.get('/api/course/categories')
}

/** 分页获取课程列表 */
export function getCourseList(params) {
  return request.get('/api/course/list', { params })
}

/** 获取课程详情 */
export function getCourseDetail(id) {
  return request.get(`/api/course/${id}`)
}

/** 搜索课程 */
export function searchCourse(params) {
  return request.get('/api/course/search', { params })
}
