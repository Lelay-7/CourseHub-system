<template>
  <AppLayout>
    <PageContainer>
      <PageHeader
        title="选课中心"
        subtitle="按教师、学分、时间和课程热度筛选适合你的课程。"
      >
        <template #extra>
          <div class="course-stats">
            <span>共 {{ courseStore.total }} 门课程</span>
            <span>已选 {{ selectionStore.selectedIds.length }} 门</span>
            <span>当前积分 {{ userStore.scoreBalance }}</span>
          </div>
        </template>
      </PageHeader>

      <Toolbar>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索课程名称、课程代码、授课教师"
          :prefix-icon="Search"
          clearable
          @input="onSearchInput"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.courseType" placeholder="课程类型" clearable @change="applyFilters">
          <el-option label="必修" :value="1" />
          <el-option label="选修" :value="2" />
        </el-select>
        <el-select v-model="filters.teacherName" placeholder="授课教师" clearable filterable @change="applyFilters">
          <el-option v-for="teacher in teacherOptions" :key="teacher" :label="teacher" :value="teacher" />
        </el-select>
        <el-select v-model="filters.creditMin" placeholder="最低学分" clearable @change="applyFilters">
          <el-option v-for="n in 4" :key="n" :label="`${n} 学分及以上`" :value="n" />
        </el-select>
        <el-select v-model="filters.timeSlot" placeholder="上课时间" clearable @change="applyFilters">
          <el-option label="周一/周二" value="early" />
          <el-option label="周三/周四" value="middle" />
          <el-option label="周五或其他" value="late" />
        </el-select>
      </Toolbar>

      <div class="category-tabs">
        <button
          v-for="cat in categoryTabs"
          :key="cat.value"
          type="button"
          :class="{ active: filters.categoryId === cat.value }"
          @click="onCategorySelect(cat.value)"
        >
          {{ cat.label }}
        </button>
      </div>

      <div class="course-result-bar">
        <div>
          <strong>共 {{ courseStore.total }} 门课程</strong>
          <span>支持课程资料、同学讨论和教师联系联动查看</span>
        </div>
        <div class="course-actions">
          <el-badge :value="compareCourses.length" :hidden="compareCourses.length === 0">
            <el-button :type="compareCourses.length ? 'warning' : 'default'" plain @click="showCompareDrawer = true">
              课程对比
            </el-button>
          </el-badge>
          <el-radio-group v-model="filters.sortBy" size="small" @change="applyFilters">
            <el-radio-button value="selection_count">人数</el-radio-button>
            <el-radio-button value="avg_rating">评分</el-radio-button>
            <el-radio-button value="credit">学分</el-radio-button>
            <el-radio-button value="create_time">最新</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div v-if="courseList.length" class="course-grid-pro">
        <CourseCard
          v-for="course in courseList"
          :key="course.course_id"
          :course="course"
          :keyword="searchKeyword"
          :is-selected="selectionStore.isSelected(course.course_id)"
          :is-compared="compareCourses.some(c => c.course_id === course.course_id)"
          @compare-toggle="toggleCompare"
          @select-course="selectCourse"
        />
      </div>
      <EmptyState v-else-if="!loading" description="暂无符合条件的课程" />

      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon>
        加载中...
      </div>
      <el-pagination
        v-if="courseStore.total > pageSize"
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="courseStore.total"
        layout="prev, pager, next, total"
        @current-change="handlePageChange"
      />

      <el-drawer v-model="showCompareDrawer" title="课程对比" size="720px">
        <div v-if="compareCourses.length" class="compare-course-list">
          <div v-for="item in compareCourses" :key="item.course_id" class="compare-course-item">
            <strong>{{ item.course_name }}</strong>
            <span>{{ item.teacher_name }} · {{ item.credit }} 学分 · {{ item.schedule }}</span>
            <el-button text type="danger" @click="removeCompare(item.course_id)">移除</el-button>
          </div>
        </div>
        <EmptyState v-else description="还没有加入对比的课程" />
      </el-drawer>

    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Loading } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Toolbar from '@/components/Toolbar.vue'
import CourseCard from '@/components/CourseCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useCourseStore } from '@/stores/course'
import { useUserStore } from '@/stores/user'
import { useSelectionStore } from '@/stores/selection'
import * as courseApi from '@/api/course'

const courseStore = useCourseStore()
const userStore = useUserStore()
const selectionStore = useSelectionStore()
const route = useRoute()
const courseList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = 12
const searchKeyword = ref('')
const compareCourses = ref([])
const showCompareDrawer = ref(false)

const filters = reactive({
  categoryId: null,
  courseType: null,
  teacherName: '',
  creditMin: null,
  timeSlot: '',
  sortBy: 'selection_count'
})

const categories = ref([])
const categoryTabs = computed(() => [
  { label: '全部课程', value: null },
  ...categories.value.map(c => ({ label: c.category_name, value: c.category_id }))
])

const teacherOptions = computed(() => [...new Set(courseList.value.map(item => item.teacher_name).filter(Boolean))])

async function fetchCourses() {
  loading.value = true
  try {
    const res = await courseApi.getCourseList({
      page: currentPage.value,
      pageSize,
      categoryId: filters.categoryId || undefined,
      courseType: filters.courseType || undefined,
      teacherName: filters.teacherName || undefined,
      creditMin: filters.creditMin || undefined,
      keyword: searchKeyword.value || undefined,
      timeSlot: filters.timeSlot || undefined,
      sortBy: filters.sortBy
    })
    if (res.code === 200) {
      const data = res.data || {}
      courseList.value = data.list || data.records || []
      courseStore.setCourseList(courseList.value, data.total || courseList.value.length)
    }
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  currentPage.value = 1
  fetchCourses()
}

function onCategorySelect(value) {
  filters.categoryId = value
  applyFilters()
}

let searchTimer = null
function onSearchInput(value) {
  if (value && value.length < 2) return
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    fetchCourses()
  }, 260)
}

function handleSearch() {
  currentPage.value = 1
  fetchCourses()
}

function handlePageChange() {
  fetchCourses()
}

function toggleCompare(course) {
  const exists = compareCourses.value.some(item => item.course_id === course.course_id)
  if (exists) {
    removeCompare(course.course_id)
    return
  }
  if (compareCourses.value.length >= 3) {
    ElMessage.warning('最多同时对比 3 门课程')
    return
  }
  compareCourses.value.push(course)
  ElMessage.success('已加入课程对比')
}

function removeCompare(id) {
  compareCourses.value = compareCourses.value.filter(item => item.course_id !== id)
}

async function selectCourse(course) {
  try {
    if (selectionStore.isSelected(course.course_id)) {
      await selectionStore.drop(course.course_id)
      course.selection_count = Math.max(Number(course.selection_count || 0) - 1, 0)
      ElMessage.success(`已取消 ${course.course_name}`)
    } else {
      await selectionStore.select(course.course_id)
      course.selection_count = Number(course.selection_count || 0) + 1
      ElMessage.success(`已选择 ${course.course_name}`)
    }
  } catch { /* handled */ }
}

async function fetchCategories() {
  try {
    const res = await courseApi.getCategories()
    categories.value = res.data?.list || res.data || []
  } catch { /* ignore */ }
}

onMounted(() => {
  if (route.query.keyword) {
    searchKeyword.value = route.query.keyword
  }
  fetchCourses()
  fetchCategories()
  selectionStore.refresh().catch(() => {})
})
</script>

<style scoped>
.course-stats {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.course-stats span {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  padding: 0 12px;
  border-radius: 999px;
  color: var(--color-primary-dark);
  background: var(--color-bg-soft);
  font-weight: 900;
}

.category-tabs {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
  margin-bottom: var(--spacing-lg);
}

.category-tabs button {
  appearance: none;
  min-height: 38px;
  border: 1px solid rgba(154, 66, 45, 0.08);
  border-radius: 999px;
  padding: 0 16px;
  color: var(--color-text-regular);
  background: rgba(255, 250, 243, 0.86);
  cursor: pointer;
  font-weight: 900;
}

.category-tabs button.active,
.category-tabs button:hover {
  color: #fff;
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.course-result-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.course-result-bar strong,
.course-result-bar span {
  display: block;
}

.course-result-bar strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.course-result-bar span {
  margin-top: 4px;
  color: var(--color-text-secondary);
}

.course-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.course-grid-pro {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: var(--spacing-lg);
}

.compare-course-list {
  display: grid;
  gap: var(--spacing-sm);
}

.compare-course-item {
  padding: var(--spacing-md);
  border-radius: 18px;
  background: var(--color-bg-soft);
}

.compare-course-item strong,
.compare-course-item span {
  display: block;
}

.compare-course-item span {
  margin-top: 4px;
  color: var(--color-text-secondary);
}

.load-more {
  text-align: center;
  padding: var(--spacing-lg);
  color: var(--color-text-secondary);
}

@media (max-width: 900px) {
  .course-result-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .course-grid-pro {
    grid-template-columns: 1fr;
  }
}
</style>
