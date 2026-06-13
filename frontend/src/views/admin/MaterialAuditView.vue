<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="资料审核" subtitle="审核用户上传的学习资料" />
      <el-radio-group v-model="filterStatus" @change="fetchList" style="margin-bottom:16px">
        <el-radio-button :value="undefined">全部</el-radio-button>
        <el-radio-button :value="0">待审核</el-radio-button>
        <el-radio-button :value="1">已发布</el-radio-button>
        <el-radio-button :value="2">已驳回</el-radio-button>
        <el-radio-button :value="3">已下架</el-radio-button>
      </el-radio-group>
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="material_id" label="ID" width="60" />
        <el-table-column prop="material_name" label="资料名称" />
        <el-table-column prop="uploader_id" label="上传者ID" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : row.status === 3 ? 'info' : 'warning'">
              {{ row.status === 1 ? '已发布' : row.status === 2 ? '已驳回' : row.status === 3 ? '已下架' : '待审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" size="small" type="success" @click="handleApprove(row.material_id)">通过</el-button>
            <el-button v-if="row.status === 0" size="small" type="danger" @click="openReject(row.material_id)">驳回</el-button>
            <el-button v-if="row.status === 1" size="small" type="danger" @click="handleDeactivate(row.material_id)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && list.length === 0" description="暂无数据" />

      <el-dialog v-model="rejectDialog" title="驳回资料" width="400px">
        <el-input v-model="rejectReason" placeholder="填写驳回原因（选填）" type="textarea" :rows="3" />
        <template #footer>
          <el-button @click="rejectDialog = false">取消</el-button>
          <el-button type="danger" @click="handleReject">确认驳回</el-button>
        </template>
      </el-dialog>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listMaterials, approveMaterial, rejectMaterial, deactivateMaterial } from '@/api/admin'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const loading = ref(false)
const filterStatus = ref(undefined)
const rejectDialog = ref(false)
const rejectId = ref(null)
const rejectReason = ref('')

async function fetchList() {
  loading.value = true
  try {
    const params = filterStatus.value !== undefined ? { status: filterStatus.value } : {}
    const res = await listMaterials(params)
    list.value = res.data?.list || []
  } finally { loading.value = false }
}

async function handleApprove(id) { await approveMaterial(id); ElMessage.success('审核通过'); fetchList() }

async function handleDeactivate(id) { await deactivateMaterial(id); ElMessage.success('已下架'); fetchList() }

function openReject(id) { rejectId.value = id; rejectReason.value = ''; rejectDialog.value = true }

async function handleReject() {
  await rejectMaterial(rejectId.value, rejectReason.value)
  ElMessage.success('已驳回')
  rejectDialog.value = false
  fetchList()
}

onMounted(fetchList)
</script>
