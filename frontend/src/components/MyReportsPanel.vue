<template>
  <section v-loading="loading">
    <ErrorState v-if="error" :title="error" @retry="fetchList" />
    <EmptyState v-else-if="!loading && !list.length" description="暂无举报记录，可在资料详情中提交举报。" />
    <article v-for="report in list" :key="report.report_id" class="report-card">
      <div class="report-title"><strong>{{ report.material_name || '资料 #' + report.material_id }}</strong><el-tag :type="report.status === 0 ? 'warning' : 'info'">{{ ['待处理', '成立并下架', '不成立'][report.status] || '已处理' }}</el-tag></div>
      <p>举报原因：{{ report.reason }}</p>
      <p v-if="report.resolution_reason">处理结论：{{ report.resolution_reason }}</p>
      <small>{{ formatTime(report.handled_time || report.create_time) }}</small>
      <el-button v-if="report.status !== 1" text type="primary" @click="$router.push('/material/' + report.material_id)">查看资料</el-button>
      <span v-else class="unavailable">该资料已下架</span>
    </article>
    <ListPagination :page="page" :page-size="pageSize" :total="total" @change="handlePageChange" />
  </section>
</template>
<script setup>
import { onMounted } from 'vue'
import request from '@/api/request'
import { usePagedList } from '@/composables/useListQuery'
import ErrorState from '@/components/ErrorState.vue'
import EmptyState from '@/components/EmptyState.vue'
import ListPagination from '@/components/ListPagination.vue'
const { list, loading, error, page, pageSize, total, fetchList, handlePageChange } = usePagedList(params => request.get('/api/reports/my', { params }))
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN') : '' }
onMounted(fetchList)
</script>
<style scoped>.report-card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 16px; padding: 20px; margin-bottom: 16px; overflow-wrap: anywhere; }.report-title { display:flex; justify-content: space-between; gap:12px; }.report-card p, .report-card small, .unavailable { color:var(--color-text-secondary); }.unavailable { margin-left: 16px; }</style>
