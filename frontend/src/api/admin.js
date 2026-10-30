/**
 * 管理员后台 API
 */
import request from './request'

const BASE = '/api/admin'

// 课程管理
export const listCourses = (params) => request.get(`${BASE}/courses`, { params })
export const createCourse = (data) => request.post(`${BASE}/courses`, data)
export const updateCourse = (id, data) => request.put(`${BASE}/courses/${id}`, data)
export const updateCourseStatus = (id, status) => request.put(`${BASE}/courses/${id}/status`, { status })
export const deleteCourse = (id) => request.delete(`${BASE}/courses/${id}`)

// 评价管理
export const listReviews = (params) => request.get(`${BASE}/reviews`, { params })
export const deleteReview = (id) => request.delete(`${BASE}/reviews/${id}`)

// 资料审核
export const listMaterials = (params) => request.get(`${BASE}/materials`, { params })
export const getAdminMaterial = (id) => request.get(`${BASE}/materials/${id}`)
export const getAdminMaterialFile = (id, revision) => request.get(`${BASE}/materials/${id}/file`, { params: { revision }, responseType: 'blob', rawResponse: true })
export const approveMaterial = (id, revision) => request.put(`${BASE}/materials/${id}/approve`, { expected_revision: revision })
export const rejectMaterial = (id, reason, revision) => request.put(`${BASE}/materials/${id}/reject`, { reason, expected_revision: revision })
export const deactivateMaterial = (id, reason) => request.put(`${BASE}/materials/${id}/deactivate`, { reason })

// 用户管理
export const listUsers = (params) => request.get(`${BASE}/users`, { params })
export const toggleUserStatus = (id, status) => request.put(`${BASE}/users/${id}/status`, { status })

// Banner 管理
export const listBanners = () => request.get(`${BASE}/banners`)
export const createBanner = (data) => request.post(`${BASE}/banners`, data)
export const updateBanner = (id, data) => request.put(`${BASE}/banners/${id}`, data)
export const deleteBanner = (id) => request.delete(`${BASE}/banners/${id}`)

// 商品管理
export const listGoods = (params) => request.get(`${BASE}/goods`, { params })
export const createGoods = (data) => request.post(`${BASE}/goods`, data)
export const updateGoods = (id, data) => request.put(`${BASE}/goods/${id}`, data)
export const updateGoodsStatus = (id, status) => request.put(`${BASE}/goods/${id}/status`, { status })
export const deleteGoods = (id) => request.delete(`${BASE}/goods/${id}`)

// 广场管理
export const listSquarePosts = (params) => request.get(`${BASE}/square`, { params })
export const deleteSquarePost = (id) => request.delete(`${BASE}/square/${id}`)
export const listSquareComments = (id) => request.get(`${BASE}/square/${id}/comments`)
export const deleteSquareComment = (id) => request.delete(`${BASE}/square/comments/${id}`)

// 订单管理
export const listAdminOrders = (params) => request.get(`${BASE}/orders`, { params })
export const getAdminOrder = (id) => request.get(`${BASE}/orders/${id}`)
export const completeOrder = (id, fulfillment_note) => request.put(`${BASE}/orders/${id}/complete`, { fulfillment_note })
export const shipOrder = (id, fulfillment_note) => request.put(`${BASE}/orders/${id}/ship`, { fulfillment_note })

// 反馈管理
export const listFeedback = (params) => request.get(`${BASE}/feedback`, { params })
export const replyFeedback = (id, reply) => request.put(`${BASE}/feedback/${id}/reply`, { reply })

// 仪表盘
export const getDashboard = () => request.get(`${BASE}/dashboard`)

// 举报管理
export const listReports = (params) => request.get(`${BASE}/reports`, { params })
export const handleReport = (id, data) => request.put(`${BASE}/reports/${id}/handle`, data)
