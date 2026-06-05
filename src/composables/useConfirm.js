/**
 * 二次确认 Composable
 * 封装 ElMessageBox.confirm，用于高危操作的确认提示
 */
import { ElMessageBox } from 'element-plus'

/**
 * 弹出二次确认弹窗
 * @param {string} action - 操作名称，如"兑换商品""取消订单""退出登录"
 * @param {string} detail - 操作详情（可选）
 * @returns {Promise<boolean>} 用户选择确认返回 true，取消返回 false
 */
export function useConfirm() {
  async function confirm(action, detail = '') {
    try {
      await ElMessageBox.confirm(
        detail ? `确认${action}？${detail}` : `确认${action}吗？`,
        '操作确认',
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: 'warning',
          center: true
        }
      )
      return true
    } catch {
      // 用户点击取消或关闭弹窗
      return false
    }
  }

  return { confirm }
}
