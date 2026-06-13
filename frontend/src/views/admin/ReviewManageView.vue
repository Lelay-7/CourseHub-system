<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="评价管理" subtitle="查看并管理课程评价" />
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="review_id" label="ID" width="60" />
        <el-table-column prop="rating" label="评分" width="80" />
        <el-table-column prop="content" label="评价内容" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-popconfirm title="确定删除该评价？" @confirm="handleDelete(row.review_id)">
              <template #reference><el-button size="small" type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && list.length === 0" description="暂无数据" />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listReviews, deleteReview } from '@/api/admin'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const loading = ref(false)
async function fetchList() { loading.value = true; try { const res = await listReviews(); list.value = res.data?.list || res.data || [] } finally { loading.value = false } }
async function handleDelete(id) { await deleteReview(id); ElMessage.success('已删除'); fetchList() }
onMounted(fetchList)
</script>
