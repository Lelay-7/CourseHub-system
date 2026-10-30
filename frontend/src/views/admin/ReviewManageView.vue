<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="评价管理" subtitle="查看并管理课程评价" />
      <ErrorState v-if="error" :title="error" @retry="fetchList" />
      <el-table v-else :data="list" border stripe v-loading="loading">
        <el-table-column prop="review_id" label="ID" width="60" />
        <el-table-column prop="rating" label="评分" width="80" />
        <el-table-column prop="content" label="评价内容" min-width="140" />
        <el-table-column label="操作" fixed="right" width="100">
          <template #default="{ row }">
            <el-popconfirm title="确定删除该评价？" @confirm="handleDelete(row.review_id)">
              <template #reference><el-button size="small" type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!error && !loading && list.length === 0" description="暂无数据" />
      <ListPagination :page="page" :page-size="pageSize" :total="total" @change="handlePageChange" />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { usePagedList } from '@/composables/useListQuery'
import ErrorState from '@/components/ErrorState.vue'
import ListPagination from '@/components/ListPagination.vue'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listReviews, deleteReview } from '@/api/admin'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

const { list, loading, error, page, pageSize, total, fetchList, resetAndFetch, handlePageChange } = usePagedList(listReviews, () => ({}))

async function handleDelete(id) { try { await deleteReview(id); ElMessage.success('已删除'); fetchList() } catch { /* request displays failure */ } }
onMounted(fetchList)
</script>

<style scoped>
.toolbar, .filters { flex-wrap: wrap; gap: 12px; }
.filters .el-input { max-width: 100%; }
@media (max-width: 767px) { .toolbar { align-items: stretch; } .filters { width: 100%; } .filters .el-input, .filters .el-select { width: 100%; max-width: 100%; } }
</style>
