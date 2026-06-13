<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="举报管理" subtitle="查看并处理资料举报" />
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="report_id" label="举报ID" width="80" />
        <el-table-column prop="material_id" label="资料ID" width="80" />
        <el-table-column prop="reporter_id" label="举报者ID" width="80" />
        <el-table-column prop="reason" label="举报原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'">
              {{ row.status === 1 ? '已处理' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="create_time" label="创建时间" width="160" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              size="small"
              type="primary"
              @click="handleHandle(row.report_id)"
            >
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && list.length === 0" description="暂无举报数据" />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listReports, handleReport } from '@/api/admin'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const res = await listReports()
    list.value = res.data?.list || []
  } finally { loading.value = false }
}

async function handleHandle(id) {
  await handleReport(id)
  ElMessage.success('已处理')
  fetchList()
}

onMounted(fetchList)
</script>
