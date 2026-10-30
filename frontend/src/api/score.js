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

/** 当前账号的月度汇总，不受明细分页影响。退款与奖励分开统计。 */
export function getScoreSummary(month) {
  return request.get('/api/score/summary', { params: { month } })
}
