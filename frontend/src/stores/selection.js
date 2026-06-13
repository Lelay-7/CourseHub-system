import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as courseApi from '@/api/course'

export const useSelectionStore = defineStore('selection', () => {
  const selectedIds = ref([])
  const userId = ref(null)

  const count = computed(() => selectedIds.value.length)
  const isSelected = id => selectedIds.value.includes(id)
  async function refresh() {
    if (userId.value == null) {
      selectedIds.value = []
      return []
    }
    const res = await courseApi.getSelectedCourses()
    const courses = res.data?.list || res.data || []
    selectedIds.value = courses.map(item => item.course_id || item.courseId).filter(Boolean)
    return courses
  }

  function setCurrentUser(value) {
    userId.value = value == null ? null : String(value)
    if (userId.value == null) selectedIds.value = []
    else refresh().catch(() => { selectedIds.value = [] })
  }

  async function select(id) {
    await courseApi.selectCourse(id)
    if (!selectedIds.value.includes(id)) selectedIds.value.push(id)
  }

  async function drop(id) {
    await courseApi.dropCourse(id)
    selectedIds.value = selectedIds.value.filter(item => item !== id)
  }

  function markSelected(id, selected) {
    if (selected && !selectedIds.value.includes(id)) selectedIds.value.push(id)
    if (!selected) selectedIds.value = selectedIds.value.filter(item => item !== id)
  }

  function clearAll() {
    selectedIds.value = []
  }

  return { selectedIds, userId, count, isSelected, setCurrentUser, refresh, select, drop, markSelected, clearAll }
})
