<template>
  <AppLayout>
    <PageContainer>
      <PageHeader :show-back="true" title="我的下载" subtitle="所有下载记录，含免费和积分下载" />
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="material_name" label="资料名称" />
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
      <EmptyState v-if="!loading && list.length === 0" description="暂无下载记录" />
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
import { ref, onMounted } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import * as materialApi from '@/api/material'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function fetchList() {
  loading.value = true
  try {
    const res = await materialApi.getMyDownloads({ page: page.value, pageSize: pageSize.value })
    const data = res.data || {}
    list.value = data.list || data.records || []
    total.value = data.total || 0
  } finally { loading.value = false }
}

function handlePageChange(p) {
  page.value = p
  fetchList()
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(fetchList)
</script>

<style scoped>
.text-success { color: #67c23a; font-weight: 700; }
.text-warning { color: #e6a23c; font-weight: 700; }
.pagination-wrap { display: flex; justify-content: center; margin-top: var(--spacing-lg); }
</style>
