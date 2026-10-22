<template>
  <AppLayout>
    <PageContainer>
      <PageHeader :show-back="true" title="我的下载" subtitle="所有下载记录，含免费和积分下载" />
      <ErrorState v-if="error" :title="error" @retry="fetchList" />
      <el-table v-else :data="list" border stripe v-loading="loading">
        <el-table-column label="资料名称" min-width="180"><template #default="{ row }"><el-button text type="primary" @click="$router.push('/material/' + row.material_id)">{{ row.material_name || '查看资料' }}</el-button></template></el-table-column>
        <el-table-column label="消耗积分" width="100">
          <template #default="{ row }">
            <span :class="row.score_cost > 0 ? 'text-warning' : 'text-success'">
              {{ row.score_cost > 0 ? `-${row.score_cost}` : '免费' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="create_time" label="下载时间" width="180">
          <template #default="{ row }">{{ formatTime(row.create_time) }}</template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!error && !loading && list.length === 0" description="暂无下载记录" />
      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="handlePageChange"
        />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { onMounted } from 'vue'
import { usePagedList } from '@/composables/useListQuery'
import ErrorState from '@/components/ErrorState.vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import * as materialApi from '@/api/material'

const { list, loading, error, page, pageSize, total, fetchList, handlePageChange } = usePagedList(materialApi.getMyDownloads)

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(fetchList)
</script>

<style scoped>
.text-success { color: var(--color-success); font-weight: 700; }
.text-warning { color: var(--color-warning); font-weight: 700; }
.pagination-wrap { display: flex; justify-content: center; margin-top: var(--spacing-lg); }
</style>
