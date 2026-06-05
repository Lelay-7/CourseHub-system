/**
 * 积分商城 API
 */
import request from './request'

/** 获取商品列表 */
export function getGoodsList(params) {
  return request.get('/api/mall/goods', { params })
}

/** 获取商品详情 */
export function getGoodsDetail(id) {
  return request.get(`/api/mall/goods/${id}`)
}

/** 兑换商品 */
export function exchangeGoods(data) {
  return request.post('/api/mall/exchange', data)
}

/** 获取兑换记录 */
export function getExchangeList(params) {
  return request.get('/api/mall/exchanges', { params })
}
