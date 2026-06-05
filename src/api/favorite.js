/**
 * 收藏 API
 */
import request from './request'

/** 获取收藏列表 */
export function getFavoriteList(params) {
  return request.get('/api/favorite/list', { params })
}

/** 添加收藏 */
export function addFavorite(data) {
  return request.post('/api/favorite/add', data)
}

/** 取消收藏 */
export function removeFavorite(data) {
  return request.post('/api/favorite/remove', data)
}
