<template>
  <AppLayout>
    <PageContainer>
      <section class="home-welcome">
        <div>
          <p>Hi，{{ userStore.userInfo?.nickname || '同学' }}</p>
          <h1>不高山选课通</h1>
          <span>完成课程评价、上传资料和参与讨论都可以获得积分，积分可用于资料下载和商城兑换。</span>
        </div>
        <strong>{{ userStore.scoreBalance }} 积分</strong>
      </section>

      <!-- 轮播图 -->
      <div v-if="banners.length > 0" class="home-banner">
        <el-carousel :interval="4000" arrow="hover" height="200px">
          <el-carousel-item v-for="b in banners" :key="b.banner_id">
            <div
              class="banner-slide"
              :style="b.image_url ? { backgroundImage: `url(${b.image_url})` } : {}"
              @click="handleBannerClick(b)"
            >
              <div class="banner-text">
                <h3>{{ b.title }}</h3>
              </div>
            </div>
          </el-carousel-item>
        </el-carousel>
      </div>

      <div class="home-search-wrapper">
        <div class="home-search">
          <el-input
            v-model="keyword"
            size="large"
            placeholder="搜索课程、资料、老师..."
            :prefix-icon="Search"
            clearable
            @input="onSearchInput"
            @keyup.enter="goSearch"
            @focus="showDropdown = searchResults.length > 0"
            @blur="scheduleHideDropdown"
          />
          <el-button type="primary" size="large" @click="goSearch">搜索</el-button>
        </div>
        <!-- 搜索下拉 -->
        <div v-if="showDropdown && searchResults.length > 0" class="search-dropdown" @mousedown.prevent>
          <template v-for="group in searchResults" :key="group.type">
            <div class="drop-group-title" @click="goSearchType(group.type)">
              {{ group.label }} <span style="font-weight:400;color:#bbb">→ 更多</span>
            </div>
            <div
              v-for="item in group.items.slice(0, 5)"
              :key="item.id"
              class="drop-item"
              @click="goItem(group.type, item.id)"
            >
              <span>{{ item.name }}</span>
              <small>{{ item.desc }}</small>
            </div>
          </template>
          <div v-if="searchResults.length >= 1" class="drop-footer" @click="goSearch">
            查看全部结果（{{ searchResults.map(g => g.label).join('、') }}）→
          </div>
        </div>
      </div>

      <section class="home-promo">
        <div>
          <StatusTag>资料有偿收集</StatusTag>
          <h2>上传优质复习资料，兑换更多学习权益</h2>
          <p>课程评价、资料下载、同学讨论和积分兑换都可以在这里串起来。</p>
        </div>
        <el-button type="primary" plain @click="$router.push('/material')">去上传资料</el-button>
      </section>

      <section class="feature-grid">
        <button
          v-for="item in featureEntries"
          :key="item.path"
          type="button"
          class="feature-item"
          @click="$router.push(item.path)"
        >
          <span :style="{ background: item.bg }">
            <el-icon :size="28"><component :is="item.icon" /></el-icon>
          </span>
          <strong>{{ item.label }}</strong>
          <small>{{ item.desc }}</small>
        </button>
      </section>

      <section class="home-section">
        <SectionTitle title="热门课程">
          <el-button text type="primary" @click="$router.push('/course')">查看更多</el-button>
        </SectionTitle>
        <div class="mini-card-grid">
          <article v-for="course in hotCourses" :key="course.course_id" class="mini-card" @click="$router.push(`/course/${course.course_id}`)">
            <StatusTag>{{ course.course_type === 1 ? '必修' : '选修' }}</StatusTag>
            <strong>{{ course.course_name }}</strong>
            <span>{{ course.teacher_name }} · {{ course.credit }} 学分</span>
          </article>
        </div>
      </section>

      <section class="home-section">
        <SectionTitle title="最新资料">
          <el-button text type="primary" @click="$router.push('/material')">查看更多</el-button>
        </SectionTitle>
        <div class="mini-card-grid">
          <article v-for="material in hotMaterials" :key="material.material_id" class="mini-card" @click="$router.push(`/material/${material.material_id}`)">
            <StatusTag :tone="material.download_score ? 'warning' : 'success'">
              {{ material.download_score ? `${material.download_score} 积分` : '免费' }}
            </StatusTag>
            <strong>{{ material.material_name }}</strong>
            <span>{{ material.uploader_name }} · {{ material.download_count }} 次下载</span>
          </article>
        </div>
      </section>

      <section class="home-section">
        <SectionTitle title="同学动态">
          <el-button text type="primary" @click="$router.push('/square')">查看更多</el-button>
        </SectionTitle>
        <div class="activity-list">
          <div v-for="post in hotPosts" :key="post.post_id" class="activity-item" @click="$router.push('/square')">
            <span>{{ (post.nickname || '?').slice(0, 1) }}</span>
            <div>
              <strong>{{ post.nickname || '匿名' }}</strong>
              <p>{{ post.content }}</p>
              <div class="post-mini-stats">
                <span>👍 {{ post.likes || 0 }}</span>
                <span>💬 {{ post.comments || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
        <EmptyState v-if="hotPosts.length === 0" description="暂无动态，快去广场发帖吧" />
      </section>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search,
  Reading,
  ChatDotRound,
  FolderOpened,
  UserFilled,
  Present
} from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import * as courseApi from '@/api/course'
import * as materialApi from '@/api/material'
import * as homeApi from '@/api/home'
import { getSquarePosts } from '@/api/square'
import { searchContactRaw } from '@/api/contact'

const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')
const hotCourses = ref([])
const hotMaterials = ref([])
const hotPosts = ref([])
const banners = ref([])
const searchResults = ref([])
const showDropdown = ref(false)
let searchTimer = null
let hideTimer = null

const featureEntries = [
  { label: '选课中心', desc: '筛选、对比、选择', path: '/course', icon: Reading, bg: 'linear-gradient(135deg, #f07462, #f8b25f)' },
  { label: '同学广场', desc: '经验与问答', path: '/square', icon: ChatDotRound, bg: 'linear-gradient(135deg, #c98272, #e87452)' },
  { label: '学习资料', desc: '预览与下载', path: '/material', icon: FolderOpened, bg: 'linear-gradient(135deg, #f4b24f, #e87452)' },
  { label: '师生通讯录', desc: '课程联系人', path: '/contact', icon: UserFilled, bg: 'linear-gradient(135deg, #86a96f, #d89334)' },
  { label: '积分商城', desc: '兑换学习权益', path: '/mall', icon: Present, bg: 'linear-gradient(135deg, #9b7cf4, #e87452)' }
]


function onSearchInput() {
  clearTimeout(searchTimer)
  const q = keyword.value.trim()
  if (!q) {
    searchResults.value = []
    showDropdown.value = false
    return
  }
  searchTimer = setTimeout(() => doSearch(q), 260)
}

async function doSearch(q) {
  try {
    const [courseRes, materialRes, contactRes] = await Promise.all([
      courseApi.getCourseList({ keyword: q, page: 1, pageSize: 5 }),
      materialApi.getMaterialList({ keyword: q, page: 1, pageSize: 5 }),
      searchContactRaw(q).catch(() => ({ data: [] }))
    ])
    const results = []
    const courses = (courseRes.data?.list || courseRes.data?.records || [])
    const materials = (materialRes.data?.list || materialRes.data?.records || [])
    const contacts = (contactRes.data || [])
    if (courses.length > 0) {
      results.push({
        type: 'course', label: '课程',
        items: courses.map(c => ({ id: c.course_id, name: c.course_name, desc: `${c.teacher_name || ''} · ${c.credit}学分` }))
      })
    }
    if (materials.length > 0) {
      results.push({
        type: 'material', label: '资料',
        items: materials.map(m => ({ id: m.material_id, name: m.material_name, desc: `${m.uploader_name || ''} · ${m.download_count || 0}次下载` }))
      })
    }
    if (contacts.length > 0) {
      results.push({
        type: 'contact', label: '教师/联系人',
        items: contacts.map(c => ({ id: c.user_id, name: c.nickname || c.real_name, desc: `${c.title || c.department || ''}` }))
      })
    }
    searchResults.value = results
    showDropdown.value = results.length > 0
  } catch { searchResults.value = [] }
}

function goItem(type, id) {
  showDropdown.value = false
  keyword.value = ''
  if (type === 'course') router.push(`/course/${id}`)
  else if (type === 'material') router.push(`/material/${id}`)
  else if (type === 'contact') router.push(`/contact/${id}`)
}

function goSearch() {
  const q = keyword.value.trim()
  if (!q) { ElMessage.warning('请输入搜索关键词'); return }
  // 先触发搜索
  doSearch(q).then(() => {
    if (searchResults.value.length === 0) {
      ElMessage.info('未找到匹配结果')
      return
    }
    showDropdown.value = false
    const types = searchResults.value.map(g => g.type)
    if (types.includes('course')) {
      router.push({ path: '/course', query: { keyword: q } })
    } else if (types.includes('material')) {
      router.push({ path: '/material', query: { keyword: q } })
    } else if (types.includes('contact')) {
      router.push({ path: '/contact', query: { keyword: q } })
    }
  })
}

function goSearchType(type) {
  showDropdown.value = false
  const q = keyword.value.trim() || ''
  const pathMap = { course: '/course', material: '/material', contact: '/contact' }
  router.push({ path: pathMap[type] || '/course', query: q ? { keyword: q } : {} })
}

function scheduleHideDropdown() {
  clearTimeout(hideTimer)
  hideTimer = setTimeout(() => { showDropdown.value = false }, 200)
}

function handleBannerClick(b) {
  if (!b.link_url) return
  if (b.link_url.startsWith('http')) {
    window.location.href = b.link_url
  } else {
    router.push(b.link_url)
  }
}

async function fetchHomeData() {
  const [courseRes, materialRes, squareRes, bannerRes] = await Promise.all([
    courseApi.getCourseList({ page: 1, pageSize: 3, sortBy: 'rating' }),
    materialApi.getMaterialList({ page: 1, pageSize: 3, sortBy: 'create_time' }),
    getSquarePosts().catch(() => ({ data: [] })),
    homeApi.getBanners().catch(() => ({ data: [] }))
  ])
  if (courseRes.code === 200) hotCourses.value = courseRes.data?.list || courseRes.data?.records || []
  if (materialRes.code === 200) hotMaterials.value = materialRes.data?.list || materialRes.data?.records || []
  const squareList = squareRes.data?.list || squareRes.data || []
  const posts = (Array.isArray(squareList) ? squareList : []).sort((a, b) => (b.likes || 0) - (a.likes || 0)).slice(0, 3)
  hotPosts.value = posts
  banners.value = (bannerRes.data || []).filter(b => b.status === 1)
}

onMounted(() => {
  fetchHomeData().catch(() => {})
})
</script>

<style scoped>
.home-welcome {
  min-height: 132px;
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-xl);
  border-radius: 28px;
  color: #fffaf3;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-lg);
  background:
    radial-gradient(circle at 12% 12%, rgba(255,255,255,.18), transparent 24%),
    linear-gradient(135deg, #ff7f73 0%, #ef6c5a 100%);
  box-shadow: var(--shadow-md);
}

.home-welcome p,
.home-welcome h1,
.home-welcome span {
  margin: 0;
}

.home-welcome p {
  color: rgba(255, 250, 243, 0.78);
}

.home-welcome h1 {
  margin-top: 4px;
  font-size: 32px;
  font-weight: 900;
}

.home-welcome span {
  display: block;
  max-width: 680px;
  margin-top: 8px;
  color: rgba(255, 250, 243, 0.84);
}

.home-welcome strong {
  flex: 0 0 auto;
  padding: 8px 14px;
  border-radius: 999px;
  color: var(--color-primary-dark);
  background: rgba(255, 250, 243, 0.92);
}

.home-banner { margin-bottom: var(--spacing-lg); border-radius: 18px; overflow: hidden; }
.banner-slide {
  width: 100%; height: 100%;
  background: linear-gradient(135deg, #f07462, #f8b25f);
  background-size: cover; background-position: center;
  display: flex; align-items: flex-end; cursor: pointer;
}
.banner-text { padding: 20px 28px; width: 100%; background: linear-gradient(transparent, rgba(0,0,0,.4)); }
.banner-text h3 { color: #fff; font-size: 20px; margin: 0; }

.home-search-wrapper { position: relative; margin-bottom: var(--spacing-lg); }
.home-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  border-radius: 26px;
  background: rgba(255, 250, 243, 0.96);
  box-shadow: var(--shadow-sm);
}
.search-dropdown {
  position: absolute; top: calc(100% + 4px); left: 0; right: 0; z-index: 100;
  background: #fff; border-radius: 18px; box-shadow: 0 12px 40px rgba(0,0,0,.12);
  max-height: 420px; overflow-y: auto; padding: 8px 0;
}
.drop-group-title { padding: 8px 16px 4px; font-size: 12px; font-weight: 800; color: #999; text-transform: uppercase; cursor: pointer; }
.drop-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 16px; cursor: pointer; transition: background .15s;
}
.drop-item:hover { background: #fef5ec; }
.drop-item span { font-weight: 700; color: #333; }
.drop-item small { color: #999; font-size: 12px; }
.drop-footer {
  padding: 10px 16px; text-align: center; color: var(--color-primary);
  font-weight: 800; cursor: pointer; border-top: 1px solid #f0e4da;
}

.home-promo {
  min-height: 174px;
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-xl);
  border-radius: 28px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--spacing-lg);
  color: #fffaf3;
  background: linear-gradient(135deg, #e87452 0%, #f4b24f 58%, #c98272 100%);
  box-shadow: var(--shadow-md);
}

.home-promo h2 {
  max-width: 560px;
  margin: var(--spacing-sm) 0;
  font-size: 28px;
  line-height: 1.25;
}

.home-promo p {
  margin: 0;
  color: rgba(255, 250, 243, 0.82);
}

.feature-grid,
.mini-card-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--spacing-lg);
}

.feature-grid {
  margin-bottom: var(--spacing-xl);
}

.feature-item,
.mini-card,
.activity-item {
  border: 1px solid rgba(154, 66, 45, 0.08);
  border-radius: 24px;
  background: var(--color-bg-card);
  box-shadow: var(--shadow-sm);
}

.feature-item {
  min-height: 132px;
  padding: var(--spacing-md);
  cursor: pointer;
}

.feature-item span {
  width: 56px;
  height: 56px;
  margin: 0 auto 10px;
  border-radius: 18px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.feature-item strong,
.feature-item small,
.mini-card strong,
.mini-card span {
  display: block;
}

.feature-item strong {
  color: var(--color-text-primary);
}

.feature-item small,
.mini-card span {
  margin-top: 5px;
  color: var(--color-text-secondary);
}

.home-section {
  margin-bottom: var(--spacing-xl);
}

.mini-card-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.mini-card {
  padding: var(--spacing-lg);
  cursor: pointer;
}

.mini-card strong {
  margin-top: var(--spacing-md);
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-list {
  display: grid;
  gap: var(--spacing-md);
}

.activity-item {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: var(--spacing-md);
  padding: var(--spacing-md);
}

.activity-item > span {
  width: 44px;
  height: 44px;
  border-radius: 16px;
  color: #fff;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
}

.activity-item strong,
.activity-item p {
  display: block;
  margin: 0;
}

.activity-item p {
  margin-top: 4px;
  color: var(--color-text-secondary);
}
.post-mini-stats { display: flex; gap: 12px; margin-top: 4px; font-size: 12px; color: #999; }
.activity-item { cursor: pointer; }

@media (max-width: 1000px) {
  .feature-grid,
  .mini-card-grid {
    grid-template-columns: 1fr;
  }

  .home-search-wrapper { margin-bottom: var(--spacing-md); }
  .home-search {
    grid-template-columns: 1fr;
  }
}
</style>
