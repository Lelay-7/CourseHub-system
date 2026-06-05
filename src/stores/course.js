/**
 * 课程状态 Store
 * 管理：课程列表缓存、筛选条件、分类数据（满足"返回保留状态"需求）
 */
import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'

export const useCourseStore = defineStore('course', () => {
  // ========== State ==========
  const categories = ref([])
  const courseList = ref([])
  const total = ref(0)

  // 筛选条件（保留状态）
  const filters = reactive({
    categoryId: null,
    courseType: null,     // 1-必修 2-选修
    creditMin: null,
    creditMax: null,
    teacherName: '',
    keyword: '',
    sortBy: 'student_count',  // student_count / rating / schedule
    sortOrder: 'desc'
  })

  // 列表滚动位置
  const scrollTop = ref(0)

  // ========== Actions ==========

  function setCategories(data) {
    categories.value = data || []
  }

  function setCourseList(list, t) {
    courseList.value = list || []
    total.value = t || 0
  }

  function appendCourseList(list, t) {
    courseList.value.push(...(list || []))
    total.value = t || total.value
  }

  function updateFilter(key, value) {
    filters[key] = value
  }

  function resetFilters() {
    Object.assign(filters, {
      categoryId: null,
      courseType: null,
      creditMin: null,
      creditMax: null,
      teacherName: '',
      keyword: '',
      sortBy: 'student_count',
      sortOrder: 'desc'
    })
  }

  function saveScrollTop(top) {
    scrollTop.value = top || 0
  }

  return {
    categories,
    courseList,
    total,
    filters,
    scrollTop,
    setCategories,
    setCourseList,
    appendCourseList,
    updateFilter,
    resetFilters,
    saveScrollTop
  }
})
