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
          <!-- 排序 -->
          <div class="sort-bar flex-between">
            <span class="total-count">共 {{ courseStore.total }} 门课程</span>
            <el-radio-group v-model="filters.sortBy" size="small" @change="applyFilters">
              <el-radio-button value="student_count">人数</el-radio-button>
              <el-radio-button value="rating">评分</el-radio-button>
              <el-radio-button value="schedule">时间</el-radio-button>
            </el-radio-group>
          </div>

          <!-- 课程卡片网格 -->
          <div v-if="courseList.length > 0" class="course-grid">
            <CourseCard
              v-for="course in courseList"
              :key="course.course_id"
              :course="course"
              @favorite="handleFavorite"
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

const filters = reactive({ ...courseStore.filters })

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
.filter-bar {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}
.search-input { flex: 1; min-width: 200px; }

.course-layout {
  display: flex;
  gap: var(--spacing-md);
}

.course-categories {
  width: 180px;
  flex-shrink: 0;
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.course-grid-wrapper {
  flex: 1;
  min-width: 0;
}

.sort-bar {
  margin-bottom: var(--spacing-md);
}
.total-count {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-md);
}

.load-more {
  text-align: center;
  padding: var(--spacing-md);
  color: var(--color-text-secondary);
}
.load-more.no-more { color: var(--color-text-placeholder); }

@media (max-width: 767px) {
  .course-grid {
    grid-template-columns: 1fr;
  }
  .filter-bar {
    flex-direction: column;
  }
}
</style>
