<template>
  <AppLayout>
    <div class="page-content course-list-page">
      <PageHeader title="课程" />

      <!-- 搜索 + 筛选栏 -->
      <div class="filter-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索课程名称、代码、教师"
          :prefix-icon="Search"
          clearable
          class="search-input"
          @input="onSearchInput"
          @clear="handleSearch"
        />
        <el-select
          v-model="filters.courseType"
          placeholder="课程类型"
          clearable
          style="width: 120px"
          @change="applyFilters"
        >
          <el-option label="必修" :value="1" />
          <el-option label="选修" :value="2" />
        </el-select>
        <el-select
          v-model="filters.teacherName"
          placeholder="授课教师"
          clearable
          filterable
          style="width: 130px"
          @change="applyFilters"
        >
          <el-option
            v-for="teacher in teacherOptions"
            :key="teacher"
            :label="teacher"
            :value="teacher"
          />
        </el-select>
        <el-select
          v-model="filters.creditMin"
          placeholder="最低学分"
          clearable
          style="width: 110px"
          @change="applyFilters"
        >
          <el-option v-for="n in 8" :key="n" :label="`≥${n}学分`" :value="n" />
        </el-select>
      </div>

      <div class="course-layout">
        <!-- 左侧分类栏 -->
        <div class="course-categories desktop-only">
          <el-menu :default-active="String(filters.categoryId || '')" @select="onCategorySelect">
            <el-menu-item index="">
              <el-icon><Grid /></el-icon> 全部课程
            </el-menu-item>
            <el-menu-item
              v-for="cat in categories"
              :key="cat.category_id"
              :index="String(cat.category_id)"
            >
              {{ cat.category_name }}
            </el-menu-item>
          </el-menu>
        </div>

        <!-- 移动端分类下拉 -->
        <el-select
          v-model="filters.categoryId"
          placeholder="选择分类"
          clearable
          class="mobile-only"
          style="width: 100%; margin-bottom: 12px"
          @change="applyFilters"
        >
          <el-option label="全部课程" :value="null" />
          <el-option
            v-for="cat in categories"
            :key="cat.category_id"
            :label="cat.category_name"
            :value="cat.category_id"
          />
        </el-select>

        <!-- 右侧课程列表 -->
        <div class="course-grid-wrapper">
          <div v-if="activeFilterTags.length > 0" class="active-filters">
            <el-tag
              v-for="tag in activeFilterTags"
              :key="tag.key"
              closable
              @close="clearFilter(tag.key)"
            >
              {{ tag.label }}
            </el-tag>
            <el-button text type="primary" size="small" @click="resetFilters">清空筛选</el-button>
          </div>

          <!-- 排序 -->
          <div class="sort-bar flex-between">
            <span class="total-count">共 {{ courseStore.total }} 门课程</span>
            <div class="sort-actions">
              <el-button
                :type="compareMode ? 'primary' : 'default'"
                size="small"
                @click="compareMode = !compareMode"
              >
                {{ compareMode ? '退出对比' : '课程对比' }}
              </el-button>
              <el-radio-group v-model="filters.sortBy" size="small" @change="applyFilters">
                <el-radio-button value="student_count">人数</el-radio-button>
                <el-radio-button value="rating">评分</el-radio-button>
                <el-radio-button value="schedule">时间</el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <transition name="compare-bar">
            <div v-if="compareMode" class="compare-bar">
              <div class="compare-selected">
                <span>已选 {{ compareCourses.length }}/3</span>
                <el-tag
                  v-for="item in compareCourses"
                  :key="item.course_id"
                  closable
                  @close="removeCompare(item.course_id)"
                >
                  {{ item.course_name }}
                </el-tag>
              </div>
              <div class="compare-actions">
                <el-button text size="small" @click="clearCompare">清空</el-button>
                <el-button
                  type="primary"
                  size="small"
                  :disabled="compareCourses.length < 2"
                  @click="showCompareDrawer = true"
                >
                  开始对比
                </el-button>
              </div>
            </div>
          </transition>
          <div v-if="compareMode && compareCourses.length === 0" class="compare-empty-tip">
            点击课程封面左下角的“加入对比”，选 2-3 门课后即可生成对比表。
          </div>

          <!-- 课程卡片网格 -->
          <div v-if="courseList.length > 0" class="course-grid">
            <CourseCard
              v-for="course in courseList"
              :key="course.course_id"
              :course="course"
              :keyword="searchKeyword"
              :selectable="compareMode"
              :selected="isCompared(course.course_id)"
              @favorite="handleFavorite"
              @compare-toggle="toggleCompare"
            />
          </div>
          <EmptyState v-else-if="!loading" description="暂无课程" />

          <div v-if="loading" class="load-more">
            <el-icon class="is-loading"><Loading /></el-icon> 加载中...
          </div>
          <div v-else-if="noMore && courseList.length > 0" class="load-more no-more">
            没有更多了
          </div>
        </div>
      </div>

      <el-drawer
        v-model="showCompareDrawer"
        title="课程对比"
        size="720px"
        class="compare-drawer"
      >
        <div class="compare-summary">
          <div v-for="item in compareCourses" :key="item.course_id" class="compare-mini-card">
            <strong>{{ item.course_name }}</strong>
            <span>{{ item.teacher_name }} · {{ item.credit }} 学分</span>
          </div>
        </div>
        <el-table :data="compareRows" border class="compare-table">
          <el-table-column prop="label" label="维度" width="110" fixed />
          <el-table-column
            v-for="item in compareCourses"
            :key="item.course_id"
            :prop="String(item.course_id)"
            :label="item.course_name"
            min-width="160"
          />
        </el-table>
        <div class="compare-tip">
          建议优先比较时间冲突、学分压力和评价情况，再决定收藏或选课。
        </div>
      </el-drawer>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated, nextTick } from 'vue'
import { Search, Loading, Grid } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import CourseCard from '@/components/CourseCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useCourseStore } from '@/stores/course'
import * as courseApi from '@/api/course'
import * as favoriteApi from '@/api/favorite'
import { ElMessage } from 'element-plus'

const courseStore = useCourseStore()

const categories = ref([])
const courseList = ref([])
const loading = ref(false)
const page = ref(1)
const noMore = ref(false)
const searchKeyword = ref('')
const compareMode = ref(false)
const compareCourses = ref([])
const showCompareDrawer = ref(false)

const filters = reactive({ ...courseStore.filters })

const teacherOptions = computed(() => {
  const names = courseList.value.map(item => item.teacher_name).filter(Boolean)
  return [...new Set(names)]
})

const activeFilterTags = computed(() => {
  const tags = []
  if (searchKeyword.value) tags.push({ key: 'keyword', label: `搜索：${searchKeyword.value}` })
  if (filters.categoryId) {
    const category = categories.value.find(item => item.category_id === Number(filters.categoryId))
    tags.push({ key: 'categoryId', label: `分类：${category?.category_name || filters.categoryId}` })
  }
  if (filters.courseType) tags.push({ key: 'courseType', label: `类型：${filters.courseType === 1 ? '必修' : '选修'}` })
  if (filters.creditMin) tags.push({ key: 'creditMin', label: `学分：≥${filters.creditMin}` })
  return tags
})

async function fetchCategories() {
  try {
    const res = await courseApi.getCategories()
    if (res.code === 200) categories.value = res.data || []
  } catch { /* handled */ }
}

async function fetchCourses(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; courseList.value = []; noMore.value = false }
  loading.value = true
  try {
    const res = await courseApi.getCourseList({
      page: page.value,
      pageSize: 20,
      ...filters,
      keyword: searchKeyword.value || undefined
    })
    if (res.code === 200) {
      const data = res.data || {}
      const list = data.list || data.records || []
      if (reset) {
        courseList.value = list
      } else {
        courseList.value.push(...list)
      }
      noMore.value = courseList.value.length >= (data.total || 0)
      courseStore.setCourseList(courseList.value, data.total || 0)
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

function applyFilters() {
  Object.assign(courseStore.filters, filters)
  fetchCourses(true)
}

function onCategorySelect(index) {
  filters.categoryId = index ? Number(index) : null
  applyFilters()
}

let searchTimer = null
function onSearchInput(val) {
  if (val && val.length < 2) return
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    filters.keyword = val
    applyFilters()
  }, 300)
}

function handleSearch() {
  filters.keyword = searchKeyword.value
  applyFilters()
}

function clearFilter(key) {
  if (key === 'keyword') {
    searchKeyword.value = ''
    filters.keyword = ''
  } else {
    filters[key] = null
  }
  applyFilters()
}

function resetFilters() {
  searchKeyword.value = ''
  Object.assign(filters, {
    keyword: '',
    categoryId: null,
    courseType: null,
    teacherName: '',
    creditMin: null,
    sortBy: filters.sortBy || 'student_count'
  })
  applyFilters()
}

function isCompared(courseId) {
  return compareCourses.value.some(item => item.course_id === courseId)
}

function toggleCompare(course) {
  if (isCompared(course.course_id)) {
    removeCompare(course.course_id)
    return
  }
  if (compareCourses.value.length >= 3) {
    ElMessage.warning('最多同时对比 3 门课程')
    return
  }
  compareCourses.value.push(course)
}

function removeCompare(courseId) {
  compareCourses.value = compareCourses.value.filter(item => item.course_id !== courseId)
}

function clearCompare() {
  compareCourses.value = []
}

const compareRows = computed(() => {
  const rows = [
    ['授课教师', item => item.teacher_name || '-'],
    ['课程类型', item => item.course_type === 1 ? '必修' : '选修'],
    ['学分', item => `${item.credit || 0} 学分`],
    ['上课时间', item => item.schedule || '-'],
    ['上课地点', item => item.location || '待定'],
    ['选课人数', item => `${item.student_count || 0} 人`],
    ['平均评分', item => `${item.avg_rating || '-'} 分`]
  ]
  return rows.map(([label, getter]) => {
    const row = { label }
    for (const item of compareCourses.value) {
      row[item.course_id] = getter(item)
    }
    return row
  })
})

async function handleFavorite(course) {
  try {
    if (course.is_favorited) {
      await favoriteApi.removeFavorite({ type: 'course', target_id: course.course_id })
      course.is_favorited = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteApi.addFavorite({ type: 'course', target_id: course.course_id })
      course.is_favorited = true
      ElMessage.success('收藏成功')
    }
  } catch { /* handled */ }
}

// 滚动加载
function onScroll() {
  const el = document.querySelector('.app-content')
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  if (scrollHeight - scrollTop - clientHeight < 200) {
    if (!noMore.value && !loading.value) {
      page.value++
      fetchCourses(false)
    }
  }
  courseStore.saveScrollTop(scrollTop)
}

onMounted(() => {
  fetchCategories()
  fetchCourses(true)
  // 恢复滚动位置
  nextTick(() => {
    const el = document.querySelector('.app-content')
    if (el && courseStore.scrollTop) el.scrollTop = courseStore.scrollTop
    el?.addEventListener('scroll', onScroll)
  })
})

onActivated(() => {
  nextTick(() => {
    const el = document.querySelector('.app-content')
    if (el && courseStore.scrollTop) el.scrollTop = courseStore.scrollTop
  })
})
</script>

<style scoped>
.course-list-page {
  --content-max-width: 1320px;
}

.filter-bar {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}
.search-input { flex: 1; min-width: 200px; }

.course-layout {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: var(--spacing-md);
  align-items: start;
}

.course-categories {
  min-height: 360px;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.course-grid-wrapper {
  min-width: 0;
  overflow: hidden;
}

.active-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  padding: var(--spacing-sm);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.78);
  border: 1px solid rgba(22, 105, 122, 0.08);
}

.sort-bar {
  margin-bottom: var(--spacing-md);
}
.sort-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
  justify-content: flex-end;
}
.total-count {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.compare-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  padding: var(--spacing-sm);
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-bg-soft), rgba(255, 253, 248, 0.92));
  border: 1px solid rgba(22, 105, 122, 0.1);
}

.compare-selected {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  min-width: 0;
}

.compare-selected > span {
  color: var(--color-primary-dark);
  font-size: var(--font-size-xs);
  font-weight: 800;
}

.compare-actions {
  display: flex;
  gap: var(--spacing-sm);
  flex-shrink: 0;
}

.compare-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.compare-mini-card {
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
}

.compare-mini-card strong,
.compare-mini-card span {
  display: block;
}

.compare-mini-card strong {
  margin-bottom: 4px;
  color: var(--color-text-primary);
}

.compare-mini-card span {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.compare-table {
  margin-bottom: var(--spacing-md);
}

.compare-tip {
  color: var(--color-text-secondary);
  line-height: 1.7;
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  background: rgba(239, 131, 84, 0.08);
}

.compare-empty-tip {
  margin: -4px 0 var(--spacing-md);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.compare-bar-enter-active,
.compare-bar-leave-active {
  transition: opacity var(--transition-normal), transform var(--transition-normal);
}
.compare-bar-enter-from,
.compare-bar-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--spacing-md);
  align-items: start;
}

.load-more {
  text-align: center;
  padding: var(--spacing-md);
  color: var(--color-text-secondary);
}
.load-more.no-more { color: var(--color-text-placeholder); }

@media (max-width: 767px) {
  .course-layout {
    grid-template-columns: 1fr;
  }
  .course-grid {
    grid-template-columns: 1fr;
  }
  .filter-bar {
    flex-direction: column;
  }
  .sort-bar,
  .compare-bar {
    align-items: flex-start;
    flex-direction: column;
  }
  .sort-actions,
  .compare-actions {
    width: 100%;
    justify-content: flex-start;
  }
  :deep(.compare-drawer) {
    width: 100% !important;
  }
}
</style>
