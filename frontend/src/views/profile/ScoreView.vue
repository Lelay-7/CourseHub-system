<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="积分明细" :show-back="true" />

      <div class="score-summary card">
        <div class="summary-item">
          <span class="summary-label">可用积分</span>
          <span class="summary-value primary">{{ userStore.scoreBalance }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">累计积分</span>
          <span class="summary-value">{{ userStore.userInfo?.total_score || 0 }}</span>
        </div>
      </div>

      <!-- 月份筛选 -->
      <el-select
        v-model="selectedMonth"
        placeholder="选择月份"
        clearable
        style="width: 160px; margin: 12px 0"
        @change="resetAndFetch()"
      >
        <el-option v-for="m in months" :key="m.value" :label="m.label" :value="m.value" />
      </el-select>

      <!-- 积分明细列表 -->
      <ErrorState v-if="error" :title="error" @retry="fetchList" />
      <div v-else-if="scoreList.length > 0" class="score-list">
        <div v-for="item in scoreList" :key="item.record_id" class="score-item card">
          <div class="score-item-left">
            <span class="score-source">{{ item.source_desc || item.sourceDesc }}</span>
            <span class="score-time">{{ formatTime(item.create_time || item.createTime) }}</span>
          </div>
          <span class="score-amount" :class="(item.change_amount || item.changeAmount) >= 0 ? 'positive' : 'negative'">
            {{ (item.change_amount || item.changeAmount) >= 0 ? '+' : '' }}{{ item.change_amount || item.changeAmount }}
          </span>
        </div>
      </div>
      <EmptyState v-else-if="!loading" description="暂无积分记录" />

      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon> 加载中...
      </div>

      <ListPagination :page="page" :page-size="pageSize" :total="total" @change="handlePageChange" />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { usePagedList, useListMemory } from '@/composables/useListQuery'
import ErrorState from '@/components/ErrorState.vue'
import ListPagination from '@/components/ListPagination.vue'

import { Loading } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import * as scoreApi from '@/api/score'

const userStore = useUserStore()
const selectedMonth = ref('')

// 生成最近12个月的选项
const months = (() => {
  const result = []
  const now = new Date()
  for (let i = 0; i < 12; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    result.push({ label: value, value })
  }
  return result
})()

const { list: scoreList, loading, error, page, pageSize, total, fetchList, resetAndFetch, handlePageChange } = usePagedList(scoreApi.getScoreDetails, () => ({ month: selectedMonth.value || undefined }))
const restoreScroll = useListMemory(() => ({ filter: selectedMonth.value, page: page.value }), state => { selectedMonth.value = state.filter; page.value = state.page })

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(async () => { await fetchList(); await restoreScroll() })
</script>

<style scoped>
.score-summary {
  display: flex; gap: var(--spacing-xl); justify-content: center;
}
.summary-item { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.summary-label { font-size: var(--font-size-xs); color: var(--color-text-secondary); }
.summary-value { font-size: var(--font-size-xxl); font-weight: 700; }
.summary-value.primary { color: var(--color-warning); }

.score-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.score-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-md);
}
.score-item-left { display: flex; flex-direction: column; gap: 2px; }
.score-source { font-weight: 500; color: var(--color-text-primary); }
.score-time { font-size: var(--font-size-xs); color: var(--color-text-placeholder); }
.score-amount { font-weight: 600; font-size: var(--font-size-md); }
.score-amount.positive { color: var(--color-success); }
.score-amount.negative { color: var(--color-danger); }

.load-more { text-align: center; padding: var(--spacing-md); color: var(--color-text-secondary); }

.score-pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--spacing-lg);
}
</style>
