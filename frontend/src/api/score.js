/**
 * 积分 API
 */
import request from './request'

/** 获取积分余额 */
export function getScoreBalance() {
  return request.get('/api/score/balance')
}

/** 分页获取积分明细 */
export function getScoreDetails(params) {
  return request.get('/api/score/details', { params })
}
