<template>
  <AppLayout>
    <PageContainer class="home-page">
      <div class="discovery-heading">
        <h1>发现课程</h1>
        <div class="home-search-wrapper">
          <el-input v-model="keyword" placeholder="搜索课程、资料、教师" :prefix-icon="Search" clearable size="large" aria-label="搜索课程、资料、教师" @input="onSearchInput" @keyup.enter="goSearch" @focus="showDropdown = searchResults.length > 0" @blur="scheduleHideDropdown" />
          <div v-if="showDropdown && searchResults.length" class="search-dropdown" @mousedown.prevent>
            <div v-for="group in searchResults" :key="group.type">
              <button class="drop-group-title" @click="goSearchType(group.type)">{{ group.label }}<span>更多 →</span></button>
              <button v-for="item in group.items" :key="item.id" class="drop-item" @click="goItem(group.type, item.id)"><span>{{ item.name }}</span><small>{{ item.desc }}</small></button>
            </div>
          </div>
        </div>
      </div>

      <section class="discovery-workspace" aria-label="课程发现">
        <div class="course-browser">
          <nav class="category-filters" aria-label="课程分类">
            <button :class="{ active: categoryId === null }" :aria-pressed="categoryId === null" @click="changeCategory(null)">全部</button>
            <button v-for="category in categories" :key="category.category_id" :class="{ active: categoryId === category.category_id }" :aria-pressed="categoryId === category.category_id" @click="changeCategory(category.category_id)">{{ category.category_name }}</button>
          </nav>
          <LoadingSkeleton v-if="coursesLoading" type="list" :rows="4" />
          <ErrorState v-else-if="coursesError" title="课程加载失败" @retry="fetchCourses" />
          <div v-else-if="courses.length" class="discovery-courses">
            <button v-for="course in courses" :key="course.course_id" class="discovery-course" :class="{ selected: selectedCourseId === course.course_id }" :aria-pressed="selectedCourseId === course.course_id" @click="chooseCourse(course)">
              <span class="course-marker" aria-hidden="true"><el-icon><Reading /></el-icon></span>
              <span class="course-copy"><strong>{{ course.course_name }}</strong><small>{{ categoryName(course) }}<span>·</span>{{ course.credit ?? '—' }} 学分<span>·</span>{{ course.teacher_name || '教师待定' }}</small></span>
              <el-icon class="course-arrow"><Right /></el-icon>
            </button>
          </div>
          <EmptyState v-else description="这个分类暂无课程" />
          <router-link class="section-link browse-all" :to="{ path: '/course', query: categoryId ? { categoryId } : {} }">查看全部课程 <el-icon><Right /></el-icon></router-link>
        </div>

        <article v-if="previewCourse" class="course-preview" :aria-busy="previewLoading">
          <div class="preview-topline">
            <span class="preview-label">{{ previewCourse.course_type === 1 ? '必修课程' : '选修课程' }}</span>
            <button class="favorite-button" :aria-label="previewCourse.is_favorited ? '取消收藏课程' : '收藏课程'" :aria-pressed="Boolean(previewCourse.is_favorited)" :disabled="previewLoading || previewError || favoriteSubmitting" @click="toggleFavorite"><el-icon :size="23"><component :is="previewCourse.is_favorited ? StarFilled : Star" /></el-icon></button>
          </div>
          <div class="preview-identity">
            <div class="preview-title-block"><h2>{{ previewCourse.course_name }}</h2><p>{{ categoryName(previewCourse) }} · {{ previewCourse.course_code || '课程预览' }}</p></div>
            <svg v-if="/算法|数据结构|数据库/.test(previewCourse.course_name || '')" class="course-graph" viewBox="0 0 200 180" aria-hidden="true">
              <path d="M100 22 52 76M100 22 148 76M52 76 25 133M52 76 79 133M148 76 121 133M148 76 175 133" />
              <circle v-for="(point, i) in graphPoints" :key="i" :cx="point[0]" :cy="point[1]" :r="i === 0 ? 17 : 14" :class="{ root: i === 0 }" />
            </svg>
            <div v-else class="course-symbol" aria-hidden="true"><el-icon><Reading /></el-icon></div>
          </div>
          <dl class="preview-facts">
            <div><dd>{{ previewCourse.credit ?? '—' }} 学分</dd><dt>学分</dt></div>
            <div><dd>{{ previewCourse.teacher_name || '待定' }}</dd><dt>授课教师</dt></div>
            <div><dd>{{ categoryName(previewCourse) }}</dd><dt>课程分类</dt></div>
          </dl>
          <div class="preview-content">
            <nav class="preview-tabs" aria-label="课程预览内容">
              <button v-for="tab in previewTabs" :key="tab.id" :class="{ active: previewTab === tab.id }" :aria-pressed="previewTab === tab.id" @click="changePreviewTab(tab.id)">{{ tab.label }}</button>
            </nav>
            <p v-if="previewLoading" class="preview-empty" role="status">正在加载课程信息…</p>
            <p v-else-if="previewError" class="preview-empty">预览加载失败，可以打开详情重试。</p>
            <p v-else-if="previewTab === 'intro'" class="preview-summary">{{ outlineText || '暂未提供课程介绍，可在详情中查看上课时间与地点。' }}</p>
            <template v-else-if="previewTab === 'materials'">
              <p v-if="previewMaterialsLoading" class="preview-empty" role="status">正在加载课程资料…</p>
              <p v-else-if="previewMaterialsError" class="preview-empty">资料加载失败，请切换标签重试。</p>
              <div v-else-if="previewMaterials.length" class="preview-related">
                <router-link v-for="material in previewMaterials" :key="material.material_id" :to="`/material/${material.material_id}`"><el-icon><Document /></el-icon><span>{{ material.material_name }}</span><el-icon><Right /></el-icon></router-link>
              </div>
              <p v-else class="preview-empty">这门课程暂无共享资料。</p>
            </template>
            <div v-else-if="previewReviews.length" class="preview-review"><span>{{ previewReviews[0].is_anonymous ? '匿名同学' : (previewReviews[0].user_name || '同学') }}</span><p>{{ previewReviews[0].content }}</p><small>更多评价可在课程详情中查看</small></div>
            <p v-else class="preview-empty">这门课程还没有评价。</p>
          </div>
          <div class="preview-actions">
            <el-button @click="router.push(`/course/${selectedCourseId}`)">查看详情 <el-icon><TopRight /></el-icon></el-button>
            <el-button type="primary" :loading="selectionSubmitting" :disabled="previewLoading || previewError || selectionStore.isSelected(selectedCourseId)" @click="selectCourse">{{ selectionStore.isSelected(selectedCourseId) ? '已加入选课' : '加入选课' }}<el-icon v-if="!selectionStore.isSelected(selectedCourseId)"><Plus /></el-icon></el-button>
          </div>
        </article>
        <div v-else class="course-preview preview-placeholder"><el-icon :size="52"><Reading /></el-icon><p>{{ coursesLoading ? '正在发现好课…' : '选择一门课程，查看详情' }}</p></div>
      </section>

      <section class="home-section material-updates" aria-labelledby="materials-title">
        <div class="section-heading"><h2 id="materials-title">资料更新</h2><router-link class="section-link" to="/material">全部资料 <el-icon><Right /></el-icon></router-link></div>
        <LoadingSkeleton v-if="materialsLoading" type="list" :rows="2" />
        <ErrorState v-else-if="materialsError" title="资料加载失败" @retry="fetchMaterials" />
        <div v-else-if="materials.length" class="resource-list">
          <router-link v-for="material in materials" :key="material.material_id" class="resource-row" :to="`/material/${material.material_id}`">
            <el-icon class="resource-icon"><Document /></el-icon><strong>{{ material.material_name }}</strong>
            <span class="resource-source">{{ material.uploader_name || '同学' }}<span>·</span>{{ material.download_score ? `${material.download_score} 积分` : '免费' }}</span>
            <time>{{ formatDate(material.create_time) }}</time><el-icon class="resource-arrow" title="查看资料并下载"><Download /></el-icon>
          </router-link>
        </div>
        <EmptyState v-else description="暂无共享资料，欢迎上传你的学习笔记" />
      </section>

      <section v-if="posts.length" class="home-section community-updates">
        <div class="section-heading"><h2>同学动态</h2><router-link class="section-link" to="/square">去广场 <el-icon><Right /></el-icon></router-link></div>
        <router-link v-for="post in posts" :key="post.post_id" class="community-row" to="/square"><span class="post-avatar">{{ (post.nickname || '同').slice(0, 1) }}</span><div><strong>{{ post.nickname || '同学' }}</strong><p>{{ post.content }}</p></div><span class="post-likes">{{ post.likes || 0 }} 赞</span></router-link>
      </section>
      <section v-if="banners.length" class="campus-notices"><h2>校园公告</h2><a v-for="banner in banners" :key="banner.banner_id" :href="safeBannerLink(banner.link_url)" @click="handleBannerClick($event, banner)">{{ banner.title }} <span>↗</span></a></section>
      <footer class="home-footer"><span>CourseHub · 不高山选课通</span><nav aria-label="学习服务"><router-link to="/contact">通讯录</router-link><router-link to="/mall">积分商城</router-link><router-link to="/profile/courses">我的选课</router-link></nav></footer>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import { Search, Reading, Right, TopRight, Plus, Star, StarFilled, Document, Download } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import { useSelectionStore } from '@/stores/selection'
import * as courseApi from '@/api/course'
import * as materialApi from '@/api/material'
import * as favoriteApi from '@/api/favorite'
import { getBanners } from '@/api/home'
import { getSquarePosts } from '@/api/square'
import { searchContactRaw } from '@/api/contact'

const router = useRouter()
const selectionStore = useSelectionStore()
const categories = ref([])
const categoryId = ref(null)
const courses = ref([])
const coursesLoading = ref(true)
const coursesError = ref(false)
const selectedCourseId = ref(null)
const courseDetail = ref(null)
const previewReviews = ref([])
const previewLoading = ref(false)
const previewError = ref(false)
const previewTab = ref('intro')
const previewTabs = [{ id: 'intro', label: '简介' }, { id: 'materials', label: '资料' }, { id: 'reviews', label: '评价' }]
const previewMaterials = ref([])
const previewMaterialsLoading = ref(false)
const previewMaterialsError = ref(false)
const selectionSubmitting = ref(false)
const favoriteSubmitting = ref(false)
const materials = ref([])
const materialsLoading = ref(true)
const materialsError = ref(false)
const posts = ref([])
const banners = ref([])
const keyword = ref('')
const searchResults = ref([])
const showDropdown = ref(false)
const graphPoints = [[100, 22], [52, 76], [148, 76], [25, 133], [79, 133], [121, 133], [175, 133]]
let courseRequest = 0
let detailRequest = 0
let materialRequest = 0
let searchRequest = 0
let searchTimer
let hideTimer
const listFrom = data => Array.isArray(data) ? data : (data?.list || data?.records || [])
const previewCourse = computed(() => courseDetail.value || courses.value.find(course => course.course_id === selectedCourseId.value))
const outlineText = computed(() => DOMPurify.sanitize(previewCourse.value?.outline || previewCourse.value?.description || '', { ALLOWED_TAGS: [], ALLOWED_ATTR: [] }).replace(/\s+/g, ' ').trim())
function categoryName(course) { return course.category_name || categories.value.find(item => item.category_id === course.category_id)?.category_name || (course.course_type === 1 ? '必修课程' : '选修课程') }
function chooseCourse(course) {
  if (window.matchMedia('(max-width: 850px)').matches) router.push(`/course/${course.course_id}`)
  else selectedCourseId.value = course.course_id
}

async function fetchCourses() {
  const request = ++courseRequest
  coursesLoading.value = true
  coursesError.value = false
  try {
    const res = await courseApi.getCourseList({ page: 1, pageSize: 4, sortBy: 'avg_rating', categoryId: categoryId.value || undefined })
    if (request !== courseRequest) return
    courses.value = listFrom(res.data)
    selectedCourseId.value = courses.value[0]?.course_id ?? null
  } catch {
    if (request === courseRequest) { coursesError.value = true; courses.value = []; selectedCourseId.value = null }
  } finally { if (request === courseRequest) coursesLoading.value = false }
}
function changeCategory(id) { if (categoryId.value === id) return; categoryId.value = id; fetchCourses() }
async function fetchPreview(id) {
  const request = ++detailRequest
  ++materialRequest
  courseDetail.value = null
  previewReviews.value = []
  previewMaterials.value = []
  previewTab.value = 'intro'
  previewError.value = false
  previewLoading.value = Boolean(id)
  if (!id) return
  try {
    const res = await courseApi.getCourseDetail(id)
    if (request !== detailRequest) return
    const data = res.data || {}
    courseDetail.value = { ...(data.course || data), is_favorited: data.is_favorited ?? data.course?.is_favorited ?? false }
    previewReviews.value = listFrom(data.reviews)
    if (data.is_selected !== undefined) selectionStore.markSelected(id, Boolean(data.is_selected))
  } catch { if (request === detailRequest) previewError.value = true }
  finally { if (request === detailRequest) previewLoading.value = false }
}
watch(selectedCourseId, fetchPreview)
async function changePreviewTab(tab) {
  previewTab.value = tab
  if (tab !== 'materials' || !selectedCourseId.value) return
  const id = selectedCourseId.value
  const request = ++materialRequest
  previewMaterialsLoading.value = true
  previewMaterialsError.value = false
  try {
    const res = await materialApi.getMaterialList({ page: 1, pageSize: 3, courseId: id })
    if (request === materialRequest && id === selectedCourseId.value) previewMaterials.value = listFrom(res.data)
  } catch { if (request === materialRequest) previewMaterialsError.value = true }
  finally { if (request === materialRequest) previewMaterialsLoading.value = false }
}
async function selectCourse() {
  const id = selectedCourseId.value
  if (!id || previewLoading.value || previewError.value || selectionSubmitting.value || selectionStore.isSelected(id)) return
  selectionSubmitting.value = true
  try { await selectionStore.select(id); ElMessage.success('已加入选课') } catch { /* Shared API handler reports errors. */ }
  finally { selectionSubmitting.value = false }
}
async function toggleFavorite() {
  const course = previewCourse.value
  if (!course || favoriteSubmitting.value || previewLoading.value || previewError.value) return
  favoriteSubmitting.value = true
  try {
    const data = { type: 'course', target_id: course.course_id }
    if (course.is_favorited) await favoriteApi.removeFavorite(data)
    else await favoriteApi.addFavorite(data)
    course.is_favorited = !course.is_favorited
    ElMessage.success(course.is_favorited ? '已收藏课程' : '已取消收藏')
  } catch { /* Shared API handler reports errors. */ }
  finally { favoriteSubmitting.value = false }
}
async function fetchMaterials() {
  materialsLoading.value = true
  materialsError.value = false
  try { const res = await materialApi.getMaterialList({ page: 1, pageSize: 3, sortBy: 'create_time' }); materials.value = listFrom(res.data) }
  catch { materialsError.value = true }
  finally { materialsLoading.value = false }
}
function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}
function safeBannerLink(value) { return /^https?:\/\//i.test(value || '') || /^\/(?!\/)/.test(value || '') ? value : '#' }
function handleBannerClick(event, banner) {
  const link = safeBannerLink(banner.link_url)
  if (link.startsWith('/')) { event.preventDefault(); router.push(link) }
  else if (link === '#') event.preventDefault()
}

function onSearchInput() {
  clearTimeout(searchTimer)
  ++searchRequest
  const q = keyword.value.trim()
  searchResults.value = []
  showDropdown.value = false
  if (q) searchTimer = setTimeout(() => doSearch(q), 260)
}
async function doSearch(q) {
  const request = ++searchRequest
  const results = await Promise.allSettled([
    courseApi.getCourseList({ keyword: q, page: 1, pageSize: 4 }),
    materialApi.getMaterialList({ keyword: q, page: 1, pageSize: 4 }),
    searchContactRaw(q)
  ])
  if (request !== searchRequest || keyword.value.trim() !== q) return []
  const groups = []
  const data = results.map(result => result.status === 'fulfilled' ? listFrom(result.value.data) : [])
  if (data[0].length) groups.push({ type: 'course', label: '课程', items: data[0].map(c => ({ id: c.course_id, name: c.course_name, desc: c.teacher_name })) })
  if (data[1].length) groups.push({ type: 'material', label: '资料', items: data[1].map(m => ({ id: m.material_id, name: m.material_name, desc: m.uploader_name })) })
  if (data[2].length) groups.push({ type: 'contact', label: '教师/联系人', items: data[2].map(c => ({ id: c.user_id, name: c.nickname || c.real_name, desc: c.title || c.department })) })
  searchResults.value = groups
  showDropdown.value = groups.length > 0
  return groups
}
function goItem(type, id) { showDropdown.value = false; router.push(`/${type}/${id}`) }
function goSearchType(type) { showDropdown.value = false; router.push({ path: `/${type}`, query: { keyword: keyword.value.trim() } }) }
async function goSearch() {
  const q = keyword.value.trim()
  if (!q) { ElMessage.info('请输入搜索关键词'); return }
  clearTimeout(searchTimer)
  const groups = await doSearch(q)
  if (keyword.value.trim() !== q) return
  if (!groups?.length) { ElMessage.info('未找到匹配结果'); return }
  goSearchType(groups[0].type)
}
function scheduleHideDropdown() { clearTimeout(hideTimer); hideTimer = setTimeout(() => { showDropdown.value = false }, 200) }
onMounted(() => {
  fetchCourses()
  fetchMaterials()
  courseApi.getCategories().then(res => { categories.value = listFrom(res.data) }).catch(() => {})
  getSquarePosts().then(res => { posts.value = [...listFrom(res.data)].sort((a, b) => (b.likes || 0) - (a.likes || 0)).slice(0, 2) }).catch(() => {})
  getBanners().then(res => { banners.value = listFrom(res.data).filter(b => b.status === 1) }).catch(() => {})
})
onUnmounted(() => { clearTimeout(searchTimer); clearTimeout(hideTimer); ++searchRequest; ++detailRequest; ++courseRequest; ++materialRequest })
</script>

<style scoped>
.home-page { padding-top: 36px; }
.discovery-heading { display: flex; align-items: center; justify-content: space-between; gap: 32px; margin-bottom: 26px; }
.discovery-heading h1 { margin: 0; font-size: clamp(30px, 3.1vw, 44px); font-weight: 800; line-height: 1.2; letter-spacing: -1.2px; }
.home-search-wrapper { position: relative; width: min(35%, 430px); }
.home-search-wrapper :deep(.el-input__wrapper) { min-height: 46px; border-radius: 8px; }
.search-dropdown { position: absolute; left: 0; right: 0; top: calc(100% + 8px); z-index: 20; max-height: 460px; overflow-y: auto; background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 8px; box-shadow: var(--shadow-md); padding: 8px; }
.drop-group-title, .drop-item { display: flex; align-items: center; justify-content: space-between; gap: 8px; width: 100%; border: 0; padding: 10px; text-align: left; color: var(--color-text-primary); background: transparent; font: inherit; cursor: pointer; }
.drop-group-title { color: var(--color-text-secondary); font-size: 11px; }
.drop-item { font-size: 13px; }
.drop-item > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.drop-item small { color: var(--color-text-secondary); flex: 0 0 auto; }
.drop-item:hover { background: var(--color-bg-soft); }
.discovery-workspace { display: grid; grid-template-columns: minmax(0, .92fr) minmax(0, 1.08fr); gap: 22px; align-items: stretch; }
.course-browser { min-width: 0; display: flex; flex-direction: column; }
.category-filters { display: flex; align-items: center; gap: 10px; margin-bottom: 22px; overflow-x: auto; scrollbar-width: thin; padding-bottom: 4px; }
.category-filters button { flex: 0 0 auto; padding: 8px 15px; border: 0; border-radius: 30px; color: var(--color-text-secondary); background: transparent; font: inherit; font-size: 14px; cursor: pointer; }
.category-filters button.active { color: var(--color-on-primary); background: var(--color-primary); font-weight: 650; }
.discovery-courses { flex: 1; }
.discovery-course { display: flex; align-items: center; gap: 22px; width: 100%; min-height: 108px; padding: 20px 24px; position: relative; border: 0; border-bottom: 1px solid var(--color-border-light); background: transparent; color: var(--color-text-primary); cursor: pointer; text-align: left; font: inherit; }
.discovery-course:hover { background: var(--color-bg-soft); }
.discovery-course.selected { background: var(--color-primary-light); border-bottom-color: transparent; }
.discovery-course.selected::before { position: absolute; left: 0; top: 0; bottom: 0; width: 3px; border-radius: 2px; content: ''; background: var(--color-primary); }
.course-marker { flex: 0 0 auto; display: grid; place-items: center; width: 28px; height: 28px; color: var(--color-primary); background: var(--color-bg-soft); border: 1px solid var(--color-border); border-radius: 4px; }
.course-copy { flex: 1; min-width: 0; }
.course-copy strong { display: block; font-size: 20px; font-weight: 700; line-height: 1.4; }
.course-copy small { display: flex; gap: 9px; flex-wrap: wrap; margin-top: 7px; color: var(--color-text-secondary); font-size: 12px; }
.course-arrow { color: var(--color-text-secondary); flex: 0 0 auto; }
.section-link { display: inline-flex; align-items: center; gap: 12px; font-size: 12px; color: var(--color-text-secondary); text-decoration: none; }
.section-link:hover { color: var(--color-primary); }
.browse-all { align-self: flex-start; margin: 25px 8px 0; padding: 5px 0; }
.course-preview { display: flex; flex-direction: column; min-width: 0; padding: 30px 34px; background: var(--color-preview-bg); border: 1px solid var(--color-border-light); }
.preview-topline { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.preview-label { display: inline-block; padding: 4px 9px; border-radius: 4px; background: var(--color-bg-soft); color: var(--color-text-secondary); font-size: 11px; }
.favorite-button { display: grid; place-items: center; width: 32px; height: 32px; padding: 0; border: 0; background: transparent; color: var(--color-text-regular); cursor: pointer; }
.favorite-button[aria-pressed='true'] { color: var(--color-primary); }
.favorite-button:disabled { opacity: .5; cursor: default; }
.preview-identity { display: flex; align-items: center; gap: 18px; margin: 18px 0 25px; min-height: 168px; }
.preview-title-block { flex: 1; min-width: 0; }
.preview-title-block h2 { margin: 0; font-size: clamp(32px, 3.5vw, 54px); line-height: 1.2; font-weight: 800; letter-spacing: -1.5px; overflow-wrap: anywhere; }
.preview-title-block p { margin: 12px 0 0; color: var(--color-text-secondary); font-size: 12px; }
.course-graph { flex: 0 0 29%; width: 29%; max-width: 190px; }
.course-graph path { fill: none; stroke: var(--color-preview-line); stroke-width: 1.3; }
.course-graph circle { fill: var(--color-preview-node); }
.course-graph circle.root { fill: var(--color-primary); }
.course-symbol { flex: 0 0 25%; text-align: center; color: var(--color-preview-line); font-size: 86px; }
.preview-facts { display: grid; grid-template-columns: .7fr 1fr 1.25fr; gap: 18px; margin: 0 0 28px; }
.preview-facts > div + div { padding-left: 20px; border-left: 1px solid var(--color-border); }
.preview-facts dd { margin: 0; font-size: 15px; font-weight: 650; overflow-wrap: anywhere; }
.preview-facts dt { color: var(--color-text-secondary); font-size: 11px; margin-top: 6px; }
.preview-content { flex: 1; border-top: 1px solid var(--color-border); }
.preview-tabs { display: flex; gap: 28px; padding-top: 14px; }
.preview-tabs button { position: relative; padding: 0 2px 14px; border: 0; background: transparent; color: var(--color-text-secondary); font: inherit; font-size: 14px; cursor: pointer; }
.preview-tabs button.active { font-weight: 650; color: var(--color-text-primary); }
.preview-tabs button.active::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 3px; border-radius: 2px; background: var(--color-primary); }
.preview-summary, .preview-empty { min-height: 48px; margin: 16px 0 0; color: var(--color-text-regular); font-size: 13px; line-height: 1.85; }
.preview-summary { display: -webkit-box; overflow: hidden; -webkit-line-clamp: 3; -webkit-box-orient: vertical; }
.preview-related { margin-top: 10px; }
.preview-related a { display: flex; align-items: center; gap: 9px; padding: 8px 0; color: var(--color-text-regular); text-decoration: none; font-size: 12px; }
.preview-related a span { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.preview-review { margin-top: 12px; color: var(--color-text-regular); font-size: 12px; }
.preview-review p { margin: 6px 0; display: -webkit-box; overflow: hidden; -webkit-line-clamp: 2; -webkit-box-orient: vertical; line-height: 1.7; }
.preview-review small { color: var(--color-text-secondary); }
.preview-actions { display: flex; gap: 12px; margin-top: 22px; flex-wrap: wrap; }
.preview-actions .el-button { border-radius: 6px; height: 42px; min-width: 132px; margin: 0; }
.preview-actions .el-icon { margin-left: 10px; }
.preview-placeholder { display: flex; align-items: center; justify-content: center; min-height: 420px; color: var(--color-text-placeholder); }
.home-section { margin-top: 32px; border-top: 1px solid var(--color-border); padding-top: 25px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 10px; }
.section-heading h2 { margin: 0; font-size: 22px; font-weight: 750; }
.resource-row { display: grid; grid-template-columns: 32px minmax(0, 1fr) minmax(160px, .6fr) 88px 22px; gap: 18px; align-items: center; min-height: 76px; border-bottom: 1px solid var(--color-border-light); padding: 10px 8px; color: var(--color-text-primary); text-decoration: none; }
.resource-row:hover { background: var(--color-bg-soft); }
.resource-icon { color: var(--color-primary); font-size: 28px; }
.resource-row strong { font-size: 14px; font-weight: 650; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resource-source { display: flex; gap: 12px; color: var(--color-text-secondary); font-size: 12px; }
.resource-row time { color: var(--color-text-secondary); font-size: 12px; text-align: right; }
.resource-arrow { color: var(--color-primary); font-size: 18px; }
.community-row { display: flex; align-items: center; gap: 16px; padding: 16px 8px; border-bottom: 1px solid var(--color-border-light); color: var(--color-text-primary); text-decoration: none; }
.post-avatar { display: grid; place-items: center; flex: 0 0 32px; height: 32px; border-radius: 50%; background: var(--color-bg-soft); color: var(--color-primary); font-size: 12px; }
.community-row > div { min-width: 0; flex: 1; }
.community-row strong { font-size: 12px; font-weight: 650; }
.community-row p { margin: 5px 0 0; color: var(--color-text-secondary); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.post-likes { flex: 0 0 auto; color: var(--color-text-secondary); font-size: 11px; }
.campus-notices { display: flex; flex-wrap: wrap; align-items: center; gap: 15px; margin-top: 28px; padding: 15px 0; }
.campus-notices h2 { margin: 0; font-size: 13px; font-weight: 650; }
.campus-notices a { color: var(--color-text-secondary); font-size: 12px; text-decoration: none; }
.home-footer { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding-top: 28px; margin-top: 30px; border-top: 1px solid var(--color-border-light); color: var(--color-text-secondary); font-size: 11px; }
.home-footer nav { display: flex; gap: 24px; }
.home-footer a { color: inherit; text-decoration: none; }
button:focus-visible, a:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 3px; }
@media (max-width: 1050px) { .course-preview { padding: 24px; } .discovery-course { padding: 18px 16px; gap: 14px; } .course-copy strong { font-size: 17px; } .preview-identity { min-height: 140px; } .course-graph { flex-basis: 24%; width: 24%; } .course-symbol { font-size: 64px; } .preview-facts { gap: 10px; } .preview-facts > div + div { padding-left: 10px; } }
@media (max-width: 850px) { .discovery-workspace { grid-template-columns: 1fr; } .course-preview { display: none; } .discovery-course.selected { background: transparent; border-bottom-color: var(--color-border-light); } .discovery-course.selected::before { display: none; } .resource-row { grid-template-columns: 28px minmax(0, 1fr) 72px 20px; gap: 12px; } .resource-source { display: none; } }
@media (max-width: 767px) { .home-page { padding-top: 24px; } .discovery-heading { align-items: stretch; flex-direction: column; gap: 18px; margin-bottom: 20px; } .home-search-wrapper { width: 100%; } .discovery-heading h1 { font-size: 32px; } .category-filters { gap: 4px; margin-bottom: 14px; } .category-filters button { padding: 7px 12px; font-size: 12px; } .discovery-course { min-height: 92px; } .course-copy strong { font-size: 17px; } .course-copy small { font-size: 11px; } .course-preview { padding: 22px; } .preview-title-block h2 { font-size: 34px; } .preview-facts dd { font-size: 13px; } .preview-actions .el-button { min-width: 0; flex: 1; } .resource-row strong { font-size: 12px; } .section-heading h2 { font-size: 20px; } .home-footer { align-items: flex-start; flex-direction: column; gap: 16px; } .home-footer nav { gap: 20px; } }
</style>
