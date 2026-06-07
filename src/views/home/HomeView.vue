<template>
  <AppLayout>
    <div class="home-page">
      <div class="page-content">
        <div class="home-hero">
          <!-- 轮播图 -->
          <el-carousel
            v-if="banners.length > 0"
            :interval="3000"
            height="260px"
            arrow="hover"
            class="home-banner"
          >
            <el-carousel-item v-for="item in banners" :key="item.id">
              <div class="banner-slide" @click="handleBannerClick(item)">
                <img :src="item.image_url" :alt="item.title" class="banner-img img-cover" />
                <div class="banner-copy">
                  <span class="eyebrow">Campus learning hub</span>
                  <h2>{{ item.title }}</h2>
                  <p>课程、资料、联系人和积分任务都在这里汇总，先看重点，再去行动。</p>
                </div>
              </div>
            </el-carousel-item>
          </el-carousel>

          <div class="today-panel">
            <div class="today-head">
              <span>今日学习</span>
              <strong>{{ userStore.scoreBalance }}</strong>
            </div>
            <p>完成签到、阅读资料和课程评价可继续获得积分。</p>
            <div class="today-progress"><span style="width: 74%"></span></div>
            <div class="today-tasks">
              <button type="button" @click="$router.push('/course')">去看课程</button>
              <button type="button" @click="$router.push('/material')">找复习资料</button>
            </div>
          </div>
        </div>

        <div class="home-stats">
          <div class="stat-item">
            <span>课程覆盖</span>
            <strong>100%</strong>
          </div>
          <div class="stat-item">
            <span>资料下载</span>
            <strong>4.2k</strong>
          </div>
          <div class="stat-item">
            <span>活跃同学</span>
            <strong>5k+</strong>
          </div>
        </div>

        <div class="todo-board">
          <div
            v-for="task in todayTodos"
            :key="task.title"
            class="todo-card"
            :class="{ done: task.done }"
            @click="$router.push(task.path)"
          >
            <div class="todo-icon">
              <el-icon><component :is="task.icon" /></el-icon>
            </div>
            <div class="todo-body">
              <strong>{{ task.title }}</strong>
              <span>{{ task.desc }}</span>
            </div>
            <em>{{ task.reward }}</em>
          </div>
        </div>

        <!-- 快捷入口：四宫格 -->
        <div class="quick-entries">
          <div
            v-for="entry in quickEntries"
            :key="entry.path"
            class="quick-entry-item"
            @click="$router.push(entry.path)"
          >
            <div class="entry-icon" :style="{ background: entry.bg }">
              <el-icon :size="28" color="#fff">
                <component :is="entry.icon" />
              </el-icon>
            </div>
            <div class="entry-text">
              <span class="entry-label">{{ entry.label }}</span>
              <small>{{ entry.desc }}</small>
            </div>
          </div>
        </div>

        <div class="focus-strip card">
          <div class="section-header flex-between">
            <h3>继续学习</h3>
            <el-button text type="primary" size="small" @click="$router.push('/course')">
              查看全部课程
            </el-button>
          </div>
          <div class="focus-scroll">
            <div
              v-for="item in continueCourses"
              :key="item.id"
              class="focus-course"
              @click="$router.push(`/course/${item.id}`)"
            >
              <span>{{ item.tag }}</span>
              <strong>{{ item.name }}</strong>
              <small>{{ item.progress }}</small>
              <div class="mini-progress"><i :style="{ width: item.width }"></i></div>
            </div>
          </div>
        </div>

        <div class="resource-strip card">
          <div class="section-header flex-between">
            <h3>推荐资料</h3>
            <el-button text type="primary" size="small" @click="$router.push('/material')">
              去资料库
            </el-button>
          </div>
          <div class="resource-row">
            <button
              v-for="item in recommendedMaterials"
              :key="item.id"
              type="button"
              class="resource-pill"
              @click="$router.push(`/material/${item.id}`)"
            >
              <span>{{ item.type }}</span>
              {{ item.name }}
            </button>
          </div>
        </div>

        <div class="demo-path card">
          <div class="section-header flex-between">
            <h3>演示路线</h3>
            <span class="demo-hint">适合答辩展示的完整前端路径</span>
          </div>
          <div class="demo-steps">
            <button
              v-for="(step, index) in demoSteps"
              :key="step.title"
              type="button"
              class="demo-step"
              @click="$router.push(step.path)"
            >
              <em>{{ index + 1 }}</em>
              <strong>{{ step.title }}</strong>
              <span>{{ step.desc }}</span>
            </button>
          </div>
        </div>

        <div class="home-columns">
          <!-- 排行榜入口 -->
          <div class="rank-section card">
          <div class="section-header flex-between">
            <h3>积分排行榜</h3>
            <el-button text type="primary" size="small" @click="showRankDialog = true">
              查看完整排行
            </el-button>
          </div>
          <div v-if="rankList.length > 0" class="rank-list">
            <div
              v-for="(item, index) in rankList.slice(0, 5)"
              :key="item.user_id"
              class="rank-item"
              :class="{ 'is-me': item.user_id === userStore.userInfo?.user_id }"
            >
              <span class="rank-num" :class="getRankClass(index)">{{ index + 1 }}</span>
              <span class="rank-name text-ellipsis">{{ item.nickname }}</span>
              <span class="rank-score">{{ item.score }} 分</span>
            </div>
          </div>
          <EmptyState v-else description="暂无排行数据" />
          </div>

          <!-- 动态列表 -->
          <div class="news-section card">
          <div class="section-header flex-between">
            <h3>课程动态</h3>
            <!-- 分类Tab -->
            <el-radio-group v-model="newsType" size="small" @change="refreshNews">
              <el-radio-button label="">全部</el-radio-button>
              <el-radio-button label="notice">课程通知</el-radio-button>
              <el-radio-button label="info">学习资讯</el-radio-button>
            </el-radio-group>
          </div>

          <div v-if="newsList.length > 0" class="news-list">
            <div
              v-for="item in newsList"
              :key="item.id"
              class="news-item card card-hover"
              @click="handleNewsClick(item)"
            >
              <h4 class="news-title text-ellipsis">{{ item.title }}</h4>
              <p class="news-summary text-ellipsis-2">{{ item.summary }}</p>
              <span class="news-time">{{ formatRelativeTime(item.publish_time) }}</span>
            </div>
          </div>
          <EmptyState v-else-if="!newsLoading" description="暂无动态" />

          <div v-if="newsLoading" class="load-more">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
          <div v-else-if="newsNoMore && newsList.length > 0" class="load-more no-more">
            没有更多了
          </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 完整排行榜弹窗 -->
    <el-dialog v-model="showRankDialog" title="积分排行榜" width="500px">
      <el-table :data="rankList" stripe max-height="400">
        <el-table-column type="index" label="排名" width="70" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="score" label="积分" width="100" />
      </el-table>
    </el-dialog>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import * as homeApi from '@/api/home'

const userStore = useUserStore()
const router = useRouter()

// ========== 轮播图 ==========
const banners = ref([])

// ========== 快捷入口 ==========
const quickEntries = [
  { path: '/course', label: '课程', desc: '筛选、收藏、评价', icon: 'Reading', bg: 'linear-gradient(135deg, #16697a, #2a9d8f)' },
  { path: '/material', label: '资料', desc: '预览与下载', icon: 'FolderOpened', bg: 'linear-gradient(135deg, #2a9d8f, #84a98c)' },
  { path: '/contact', label: '通讯录', desc: '师生快速联系', icon: 'UserFilled', bg: 'linear-gradient(135deg, #ef8354, #e9a23b)' },
  { path: '/mall', label: '商城', desc: '积分兑换奖励', icon: 'ShoppingCart', bg: 'linear-gradient(135deg, #d95d5d, #ef8354)' }
]

const todayTodos = [
  { title: '完成每日签到', desc: '连续签到可获得额外奖励', reward: '+10', icon: 'CalendarChecked', path: '/profile/score', done: true },
  { title: '评价一门课程', desc: '写下真实体验，帮助同学选课', reward: '+3', icon: 'ChatDotRound', path: '/course', done: false },
  { title: '下载复习资料', desc: '期末资料已按热度整理', reward: '资料', icon: 'Download', path: '/material', done: false }
]

const continueCourses = [
  { id: 2, tag: '实验课', name: 'Web 前端工程实践', progress: '已学 7 / 12 课时', width: '58%' },
  { id: 1, tag: '核心课', name: '算法与数据结构', progress: '已学 10 / 16 课时', width: '66%' },
  { id: 3, tag: '复习中', name: '数据库系统原理', progress: '已学 8 / 14 课时', width: '54%' }
]

const recommendedMaterials = [
  { id: 1, type: 'PDF', name: '期末复习提纲与知识图谱' },
  { id: 2, type: 'Word', name: 'Vue3 组件设计实验指导书' },
  { id: 5, type: 'PPT', name: '软件工程答辩 PPT 范例' },
  { id: 3, type: 'Excel', name: '数据库 SQL 练习题集' }
]

const demoSteps = [
  { title: '搜课程', desc: '筛选、关键词高亮、进详情', path: '/course' },
  { title: '收藏课程', desc: '即时反馈，进入我的收藏', path: '/course/2' },
  { title: '下载资料', desc: '积分确认与余额变化', path: '/material/2' },
  { title: '兑换商品', desc: '确认弹窗、成功状态、库存变化', path: '/mall/1' },
  { title: '看订单', desc: '状态筛选与时间线详情', path: '/profile/orders' }
]

// ========== 排行榜 ==========
const rankList = ref([])
const showRankDialog = ref(false)

// ========== 动态列表 ==========
const newsList = ref([])
const newsType = ref('')
const newsPage = ref(1)
const newsLoading = ref(false)
const newsNoMore = ref(false)

async function fetchBanners() {
  try {
    const res = await homeApi.getBanners()
    if (res.code === 200) banners.value = res.data || []
  } catch { /* error handled by interceptor */ }
}

async function fetchRankList() {
  try {
    const res = await homeApi.getRankList({ limit: 100 })
    if (res.code === 200) rankList.value = res.data?.list || res.data || []
  } catch { /* error handled */ }
}

async function fetchNews(reset = false) {
  if (newsLoading.value) return
  if (reset) {
    newsPage.value = 1
    newsList.value = []
    newsNoMore.value = false
  }
  newsLoading.value = true
  try {
    const res = await homeApi.getNewsList({
      page: newsPage.value,
      pageSize: 20,
      type: newsType.value || undefined
    })
    if (res.code === 200) {
      const data = res.data || {}
      const list = data.list || data.records || []
      if (reset) {
        newsList.value = list
      } else {
        newsList.value.push(...list)
      }
      newsNoMore.value = newsList.value.length >= (data.total || 0)
    }
  } catch { /* error handled */ }
  finally { newsLoading.value = false }
}

function refreshNews() {
  fetchNews(true)
}

function loadMoreNews() {
  if (newsNoMore.value || newsLoading.value) return
  newsPage.value++
  fetchNews(false)
}

// 滚动加载
function onScroll() {
  const mainEl = document.querySelector('.app-content')
  if (!mainEl) return
  const { scrollTop, scrollHeight, clientHeight } = mainEl
  if (scrollHeight - scrollTop - clientHeight < 200) {
    loadMoreNews()
  }
}

function getRankClass(index) {
  if (index === 0) return 'gold'
  if (index === 1) return 'silver'
  if (index === 2) return 'bronze'
  return ''
}

function formatRelativeTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diff = now - d
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return d.toLocaleDateString('zh-CN')
}

function handleBannerClick(item) {
  if (item.link_url) {
    if (item.link_url.startsWith('/')) {
      router.push(item.link_url)
    } else {
      window.open(item.link_url, '_blank')
    }
  }
}

function handleNewsClick(item) {
  if (item.link_url) {
    if (item.link_url.startsWith('/')) {
      router.push(item.link_url)
    } else {
      window.open(item.link_url, '_blank')
    }
  }
}

onMounted(() => {
  fetchBanners()
  fetchRankList()
  fetchNews(true)

  // 监听滚动
  const mainEl = document.querySelector('.app-content')
  if (mainEl) mainEl.addEventListener('scroll', onScroll)
})
</script>

<style scoped>
.home-page {
  padding-bottom: var(--spacing-xl);
}

.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  align-items: stretch;
}

.home-banner {
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-md);
}

.banner-slide {
  position: relative;
  height: 100%;
  cursor: pointer;
  overflow: hidden;
}

.banner-slide::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(10, 43, 54, 0.62), rgba(10, 43, 54, 0.16) 58%, rgba(10, 43, 54, 0.05));
  pointer-events: none;
}

.banner-img {
  transform: scale(1.02);
  transition: transform var(--transition-slow);
}

.banner-slide:hover .banner-img {
  transform: scale(1.06);
}

.banner-copy {
  position: absolute;
  left: 40px;
  right: 40px;
  bottom: 34px;
  z-index: 1;
  max-width: 560px;
  color: #fff;
  text-shadow: 0 2px 14px rgba(0, 0, 0, 0.25);
}

.eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.banner-copy h2 {
  margin: 0;
  font-size: 34px;
  line-height: 1.18;
}

.banner-copy p {
  margin: 10px 0 0;
  color: rgba(255, 255, 255, 0.86);
  line-height: 1.7;
}

.today-panel {
  min-height: 260px;
  padding: var(--spacing-lg);
  border-radius: var(--radius-md);
  background:
    linear-gradient(160deg, rgba(255, 253, 248, 0.96), rgba(238, 247, 245, 0.96));
  border: 1px solid rgba(22, 105, 122, 0.12);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.today-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.today-head span {
  color: var(--color-text-secondary);
  font-weight: 700;
}

.today-head strong {
  color: var(--color-accent);
  font-size: 42px;
  line-height: 1;
}

.today-panel p {
  margin: 0;
  color: var(--color-text-regular);
  line-height: 1.7;
}

.today-progress {
  height: 8px;
  border-radius: 999px;
  background: rgba(22, 105, 122, 0.1);
  overflow: hidden;
}

.today-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-primary), var(--color-accent));
}

.today-tasks {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-sm);
}

.today-tasks button {
  border: 0;
  border-radius: var(--radius-sm);
  padding: 10px;
  cursor: pointer;
  color: var(--color-primary-dark);
  background: #fff;
  font-weight: 700;
  box-shadow: 0 0 0 1px rgba(22, 105, 122, 0.12) inset;
}

.today-tasks button:hover {
  color: #fff;
  background: var(--color-primary);
}

.home-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.stat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.78);
  border: 1px solid rgba(22, 105, 122, 0.08);
}

.stat-item span {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.stat-item strong {
  color: var(--color-primary-dark);
  font-size: var(--font-size-xl);
}

.quick-entries {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.todo-board {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.todo-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 14px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(22, 105, 122, 0.1);
  background: rgba(255, 253, 248, 0.9);
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast), border-color var(--transition-fast);
}

.todo-card:hover {
  transform: translateY(-2px);
  border-color: rgba(239, 131, 84, 0.35);
  box-shadow: var(--shadow-md);
}

.todo-card.done {
  background: linear-gradient(135deg, rgba(217, 240, 238, 0.9), rgba(255, 253, 248, 0.95));
}

.todo-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: var(--color-primary);
  flex-shrink: 0;
}

.todo-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.todo-body strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
}

.todo-body span {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.todo-card em {
  color: var(--color-accent);
  font-style: normal;
  font-weight: 800;
}

.quick-entry-item {
  display: flex;
  align-items: center;
  flex-direction: row;
  gap: var(--spacing-sm);
  cursor: pointer;
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.92);
  border: 1px solid rgba(22, 105, 122, 0.08);
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}
.quick-entry-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.entry-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.entry-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.entry-label {
  font-size: var(--font-size-base);
  color: var(--color-text-primary);
  font-weight: 700;
}

.entry-text small {
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.focus-strip,
.resource-strip {
  margin-bottom: var(--spacing-md);
}

.demo-path {
  margin-bottom: var(--spacing-md);
}

.demo-hint {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.demo-steps {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--spacing-sm);
}

.demo-step {
  min-height: 104px;
  border: 1px solid rgba(22, 105, 122, 0.12);
  border-radius: var(--radius-md);
  padding: var(--spacing-sm);
  color: var(--color-text-primary);
  background: #fff;
  cursor: pointer;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast), border-color var(--transition-fast);
}

.demo-step:hover {
  transform: translateY(-2px);
  border-color: rgba(239, 131, 84, 0.38);
  box-shadow: var(--shadow-md);
}

.demo-step em {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: var(--color-primary);
  font-style: normal;
  font-size: var(--font-size-xs);
  font-weight: 800;
}

.demo-step strong {
  font-size: var(--font-size-sm);
}

.demo-step span {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.focus-scroll {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: var(--spacing-sm);
}

.focus-course {
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  background: var(--color-bg-soft);
  cursor: pointer;
  transition: transform var(--transition-fast), background var(--transition-fast);
}

.focus-course:hover {
  transform: translateY(-2px);
  background: #e2f2ee;
}

.focus-course span {
  display: inline-flex;
  margin-bottom: 8px;
  padding: 3px 8px;
  border-radius: 999px;
  color: var(--color-primary-dark);
  background: #fff;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.focus-course strong,
.focus-course small {
  display: block;
}

.focus-course strong {
  margin-bottom: 6px;
  color: var(--color-text-primary);
}

.focus-course small {
  color: var(--color-text-secondary);
}

.mini-progress {
  height: 6px;
  margin-top: var(--spacing-sm);
  border-radius: 999px;
  overflow: hidden;
  background: rgba(22, 105, 122, 0.12);
}

.mini-progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-primary), var(--color-accent));
}

.resource-row {
  display: flex;
  gap: var(--spacing-sm);
  overflow-x: auto;
  padding-bottom: 2px;
}

.resource-pill {
  flex: 0 0 auto;
  border: 1px solid rgba(22, 105, 122, 0.12);
  border-radius: 999px;
  padding: 9px 14px;
  background: #fff;
  color: var(--color-text-regular);
  cursor: pointer;
  font-weight: 700;
}

.resource-pill:hover {
  color: #fff;
  background: var(--color-primary);
}

.resource-pill span {
  margin-right: 6px;
  color: var(--color-accent);
}

.section-header {
  margin-bottom: var(--spacing-md);
}
.section-header h3 {
  margin: 0;
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
}

.rank-section {
  margin-bottom: 0;
}

.home-columns {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: var(--spacing-md);
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.rank-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}
.rank-item.is-me {
  background: var(--color-primary-light);
  border-radius: var(--radius-sm);
}
.rank-item:hover {
  background: var(--color-bg-page);
}

.rank-num {
  width: 24px;
  text-align: center;
  font-weight: 700;
  color: var(--color-text-secondary);
}
.rank-num.gold { color: #f5a623; }
.rank-num.silver { color: #9b9b9b; }
.rank-num.bronze { color: #d4884a; }

.rank-name {
  flex: 1;
  color: var(--color-text-regular);
}
.rank-score {
  color: var(--color-warning);
  font-weight: 600;
}

.news-section {
  min-width: 0;
}

.news-item {
  margin-bottom: var(--spacing-sm);
  cursor: pointer;
  box-shadow: none;
}
.news-title {
  margin: 0 0 var(--spacing-xs);
  font-size: var(--font-size-base);
  color: var(--color-text-primary);
}
.news-summary {
  margin: 0 0 var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
.news-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
}

.load-more {
  text-align: center;
  padding: var(--spacing-md);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
.load-more.no-more {
  color: var(--color-text-placeholder);
}

@media (max-width: 767px) {
  .home-hero,
  .home-columns {
    grid-template-columns: 1fr;
  }
  .home-stats {
    grid-template-columns: 1fr;
  }
  .todo-board,
  .focus-scroll,
  .demo-steps {
    grid-template-columns: 1fr;
  }
  .quick-entries {
    grid-template-columns: 1fr 1fr;
    gap: var(--spacing-sm);
  }
  .quick-entry-item {
    align-items: flex-start;
    flex-direction: column;
  }
  .entry-icon {
    width: 44px;
    height: 44px;
  }
  .banner-copy h2 {
    font-size: 24px;
  }
  .banner-copy {
    left: var(--spacing-md);
    right: var(--spacing-md);
    bottom: var(--spacing-md);
  }
}
</style>
