/**
 * 首页 API
 */
import request from './request'

/** 获取首页轮播图 */
export function getBanners() {
  return request.get('/api/home/banners')
}

/** 获取首页动态列表 */
export function getNewsList(params) {
  return request.get('/api/home/news', { params })
}

/** 获取待处理订单数量 */
export function getPendingOrderCount() {
  return request.get('/api/home/order/count')
}

/** 获取积分排行榜 */
export function getRankList(params) {
  return request.get('/api/home/rank', { params })
}
