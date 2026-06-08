/**
 * 资料状态 Store
 * 管理：资料列表缓存、筛选条件（满足"返回保留状态"需求）
 */
import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'

export const useMaterialStore = defineStore('material', () => {
  // ========== State ==========
  const materialList = ref([])
  const total = ref(0)

  // 筛选条件（保留状态）
  const filters = reactive({
    categoryId: null,
    materialType: null,   // 1-PDF 2-Word 3-Excel 4-PPT 5-图片 6-压缩包
    courseId: null,
    keyword: '',
    timeRange: 0,         // 7/30/0(全部)
    sortBy: 'download_count',
    sortOrder: 'desc'
  })

  const scrollTop = ref(0)

  // ========== Actions ==========

  function setMaterialList(list, t) {
    materialList.value = list || []
    total.value = t || 0
  }

  function appendMaterialList(list, t) {
    materialList.value.push(...(list || []))
    total.value = t || total.value
  }

  function updateFilter(key, value) {
    filters[key] = value
  }

  function resetFilters() {
    Object.assign(filters, {
      categoryId: null,
      materialType: null,
      courseId: null,
      keyword: '',
      timeRange: 0,
      sortBy: 'download_count',
      sortOrder: 'desc'
    })
  }

  function saveScrollTop(top) {
    scrollTop.value = top || 0
  }

  return {
    materialList,
    total,
    filters,
    scrollTop,
    setMaterialList,
    appendMaterialList,
    updateFilter,
    resetFilters,
    saveScrollTop
  }
})
