<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="我的评价" :show-back="true" />

      <div v-if="reviewList.length > 0" class="review-list">
        <div v-for="item in reviewList" :key="item.review_id" class="review-item card">
          <div class="review-item-left">
            <router-link class="review-source" :to="`/course/${item.course_id}`">
              {{ item.course_name }}（{{ item.course_code }}）
            </router-link>
            <span class="review-teacher">授课教师：{{ item.teacher_name || '未设置' }}</span>
            <p>{{ item.content || '未填写评价内容' }}</p>
            <span class="review-time">{{ formatTime(item.create_time) }}</span>
          </div>
          <div class="review-rating">{{ '★'.repeat(item.rating || 0) }}<span>{{ item.rating }} 分</span></div>
        </div>
      </div>
      <EmptyState v-else-if="!loading" description="暂无评价记录" />

      <div v-if="total > pageSize" class="review-pagination">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="handlePageChange"
        />
      </div>

      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon> 加载中...
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import * as courseApi from '@/api/course'

const reviewList = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function fetchList(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; reviewList.value = [] }
  loading.value = true
  try {
    const res = await courseApi.getMyReviews()
    if (res.code === 200) {
      const data = res.data || {}
      const list = data.list || data.records || data || []
      total.value = list.length
      reviewList.value = list.slice((page.value - 1) * pageSize.value, page.value * pageSize.value)
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

function handlePageChange(p) {
  page.value = p
  fetchList()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => { fetchList(true) })
</script>

<style scoped>
.review-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}
.review-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-md);
}
.review-item-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.review-source {
  font-weight: 500;
  color: var(--color-text-primary);
}
.review-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
}
.review-teacher { color: var(--color-text-secondary); }
.review-item p { margin: 6px 0; color: var(--color-text-regular); }
.review-rating { color: var(--color-warning); font-weight: 700; white-space: nowrap; }
.review-rating span { margin-left: 8px; color: var(--color-text-secondary); }

.review-pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--spacing-lg);
}
.load-more {
  text-align: center;
  padding: var(--spacing-md);
  color: var(--color-text-secondary);
}
</style>
