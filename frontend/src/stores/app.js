/**
 * 全局应用状态 Store
 * 管理：侧边栏折叠、页面加载、待处理订单数、视口模式
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAppStore = defineStore('app', () => {
  // ========== State ==========
  const sidebarCollapsed = ref(false)
  const pageLoading = ref(false)
  const pendingOrderCount = ref(0)
  const isMobile = ref(false)

  // ========== Getters ==========
  const sidebarWidth = computed(() =>
    sidebarCollapsed.value ? '64px' : '220px'
  )
  const orderBadgeText = computed(() =>
    pendingOrderCount.value > 99 ? '99+' : String(pendingOrderCount.value)
  )
  const showOrderBadge = computed(() => pendingOrderCount.value > 0)

  // ========== Actions ==========

  /** 切换侧边栏折叠 */
  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  /** 设置页面加载状态 */
  function setPageLoading(loading) {
    pageLoading.value = loading
  }

  /** 更新待处理订单数 */
  function setPendingOrderCount(count) {
    pendingOrderCount.value = count || 0
  }

  /** 检查视口尺寸，判断是否为移动端 */
  function checkViewport() {
    isMobile.value = window.innerWidth < 768
  }

  return {
    sidebarCollapsed,
    pageLoading,
    pendingOrderCount,
    isMobile,
    sidebarWidth,
    orderBadgeText,
    showOrderBadge,
    toggleSidebar,
    setPageLoading,
    setPendingOrderCount,
    checkViewport
  }
}, {
  persist: {
    key: 'app-store',
    storage: localStorage,
    paths: ['sidebarCollapsed']
  }
})
