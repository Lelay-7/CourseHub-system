/**
 * 分页加载 Composable
 * 封装列表的分页加载逻辑：数据、页码、加载状态、到底判断
 *
 * @param {Function} fetchFn - 接受 { page, pageSize, ...params } 返回 Promise<{ list, total }>
 * @param {Object} options - { pageSize?: number }
 */
import { ref, reactive, computed } from 'vue'

export function usePagination(fetchFn, options = {}) {
  const pageSize = options.pageSize || 20
  const list = ref([])
  const total = ref(0)
  const page = ref(1)
  const loading = ref(false)
  const noMore = computed(() => list.value.length >= total.value)
  const isEmpty = computed(() => !loading.value && list.value.length === 0)

  /** 加载数据（追加模式） */
  async function load(params = {}) {
    if (loading.value) return
    loading.value = true
    try {
      const res = await fetchFn({ page: page.value, pageSize, ...params })
      if (res && res.code === 200) {
        const data = res.data || {}
        list.value.push(...(data.list || data.records || []))
        total.value = data.total || 0
        return data
      }
    } catch (err) {
      console.error('[usePagination] load error', err)
    } finally {
      loading.value = false
    }
  }

  /** 加载下一页 */
  async function loadMore(params) {
    if (noMore.value || loading.value) return
    page.value++
    await load(params)
  }

  /** 重置并重新加载 */
  async function refresh(params = {}) {
    page.value = 1
    list.value = []
    total.value = 0
    await load(params)
  }

  return {
    list,
    total,
    page,
    loading,
    noMore,
    isEmpty,
    load,
    loadMore,
    refresh
  }
}
