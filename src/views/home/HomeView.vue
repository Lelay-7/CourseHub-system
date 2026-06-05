<template>
  <AppLayout>
    <div class="home-page">
      <div class="page-content">
        <!-- 轮播图 -->
        <el-carousel
          v-if="banners.length > 0"
          :interval="3000"
          height="200px"
          arrow="hover"
          class="home-banner"
        >
          <el-carousel-item v-for="item in banners" :key="item.id">
            <img
              :src="item.image_url"
              :alt="item.title"
              class="banner-img img-cover"
              @click="handleBannerClick(item)"
            />
          </el-carousel-item>
        </el-carousel>

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
            <span class="entry-label">{{ entry.label }}</span>
          </div>
        </div>

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
        <div class="news-section">
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
import { Loading } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import * as homeApi from '@/api/home'

const userStore = useUserStore()

// ========== 轮播图 ==========
const banners = ref([])

// ========== 快捷入口 ==========
const quickEntries = [
  { path: '/course', label: '课程', icon: 'Reading', bg: 'linear-gradient(135deg, #409EFF, #337ECC)' },
  { path: '/material', label: '资料', icon: 'FolderOpened', bg: 'linear-gradient(135deg, #67C23A, #529B2E)' },
  { path: '/contact', label: '通讯录', icon: 'UserFilled', bg: 'linear-gradient(135deg, #E6A23C, #C98E31)' },
  { path: '/mall', label: '商城', icon: 'ShoppingCart', bg: 'linear-gradient(135deg, #F56C6C, #D14343)' }
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
    window.open(item.link_url, '_blank')
  }
}

function handleNewsClick(item) {
  if (item.link_url) {
    window.open(item.link_url, '_blank')
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

.home-banner {
  margin-bottom: var(--spacing-lg);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.banner-img {
  cursor: pointer;
}

.quick-entries {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.quick-entry-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}
.quick-entry-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.entry-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-round);
  display: flex;
  align-items: center;
  justify-content: center;
}

.entry-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  font-weight: 500;
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
  margin-bottom: var(--spacing-lg);
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

.news-item {
  margin-bottom: var(--spacing-sm);
  cursor: pointer;
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
  .quick-entries {
    grid-template-columns: repeat(4, 1fr);
    gap: var(--spacing-sm);
  }
  .entry-icon {
    width: 44px;
    height: 44px;
  }
  .home-banner {
    height: 160px;
  }
}
</style>
