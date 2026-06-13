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

/** 提交课程评价 */
export function submitCourseReview(id, data) {
  return request.post(`/api/course/${id}/review`, data)
}

/** 删除自己的课程评价 */
export function deleteCourseReview(id) {
  return request.delete(`/api/course/${id}/review`)
}

/** 获取当前用户已选课程 */
export function getSelectedCourses() {
  return request.get('/api/course/selected')
}

/** 选择课程 */
export function selectCourse(id) {
  return request.post(`/api/course/${id}/select`)
}

/** 退选课程 */
export function dropCourse(id) {
  return request.delete(`/api/course/${id}/select`)
}

/** 获取当前用户的真实课程评价 */
export function getMyReviews() {
  return request.get('/api/course/reviews/my')
}
