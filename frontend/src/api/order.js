/**
 * 订单 API
 */
import request from './request'

/** 分页获取订单列表 */
export function getOrderList(params) {
  return request.get('/api/order/list', { params })
}

/** 获取订单详情 */
export function getOrderDetail(id) {
  return request.get(`/api/order/${id}`)
}

/** 取消订单 */
export function cancelOrder(id) {
  return request.post(`/api/order/${id}/cancel`)
}

/** 删除已完成或已取消的订单记录 */
export function deleteOrder(id) {
  return request.delete(`/api/order/${id}`)
}
