/**
 * 权限指令 v-permission
 * 根据用户角色控制元素可见性
 *
 * 用法：
 *   <el-button v-permission="[1, 3]">仅学生和管理员可见</el-button>
 *   <el-button v-permission="2">仅教师可见</el-button>
 *
 * 参数为允许访问的 user_type 数组
 */
import { useUserStore } from '@/stores/user'

export default {
  mounted(el, binding) {
    const userStore = useUserStore()
    const allowedTypes = binding.value

    // 如果未传值或传入空数组，默认显示
    if (!allowedTypes || allowedTypes.length === 0) return

    const userType = userStore.userInfo?.user_type
    if (userType === undefined || userType === null) {
      el.style.display = 'none'
      return
    }

    if (!allowedTypes.includes(userType)) {
      el.style.display = 'none'
    }
  }
}
