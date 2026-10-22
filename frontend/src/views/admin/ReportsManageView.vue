<template>
  <AppLayout><PageContainer>
    <PageHeader title="举报管理" subtitle="核查资料内容，记录结论并反馈给举报人。" />
    <el-radio-group v-model="filterStatus" @change="resetAndFetch" class="status-filter"><el-radio-button value="">全部</el-radio-button><el-radio-button :value="0">待处理</el-radio-button><el-radio-button :value="1">成立并下架</el-radio-button><el-radio-button :value="2">不成立</el-radio-button></el-radio-group>
    <ErrorState v-if="error" :title="error" @retry="fetchList" />
    <el-table v-else :data="list" border stripe v-loading="loading">
      <el-table-column prop="report_id" label="举报 ID" width="90" />
      <el-table-column label="资料" min-width="170"><template #default="{ row }"><el-button text type="primary" @click="$router.push({ path: '/admin/materials', query: { materialId: row.material_id } })">{{ row.material_name || '资料 #' + row.material_id }}</el-button></template></el-table-column>
      <el-table-column prop="reason" label="举报原因" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" min-width="120"><template #default="{ row }">{{ ['待处理', '成立并下架', '不成立'][row.status] || '未知' }}</template></el-table-column>
      <el-table-column prop="resolution_reason" label="处理结论" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" fixed="right" width="100"><template #default="{ row }"><el-button v-if="row.status === 0" size="small" type="primary" @click="openHandle(row)">处理</el-button></template></el-table-column>
    </el-table>
    <ListPagination :page="page" :page-size="pageSize" :total="total" @change="handlePageChange" />
    <el-dialog v-model="visible" title="处理举报" width="min(580px, 94vw)" :close-on-click-modal="!saving" :show-close="!saving">
      <p>{{ current?.material_name || '资料 #' + current?.material_id }}：{{ current?.reason }}</p>
      <p><el-button text type="primary" @click="inspectMaterial">前往核查资料</el-button></p>
      <el-form label-position="top"><el-form-item label="处理结果"><el-radio-group v-model="action" :disabled="saving"><el-radio value="deactivate">举报成立并下架</el-radio><el-radio value="dismiss">举报不成立</el-radio></el-radio-group></el-form-item><el-form-item label="处理原因（举报人可见）"><el-input v-model="reason" type="textarea" :rows="4" maxlength="500" show-word-limit :disabled="saving" /></el-form-item></el-form>
      <p v-if="actionError" role="alert">{{ actionError }}</p>
      <template #footer><el-button :disabled="saving" @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存处理结果</el-button></template>
    </el-dialog>
  </PageContainer></AppLayout>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listReports, handleReport } from '@/api/admin'
import { usePagedList, useRequestGate } from '@/composables/useListQuery'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import ErrorState from '@/components/ErrorState.vue'
import ListPagination from '@/components/ListPagination.vue'
const router = useRouter(), filterStatus = ref('')
const { list, loading, error, page, pageSize, total, fetchList, resetAndFetch, handlePageChange } = usePagedList(listReports, () => ({ status: filterStatus.value === '' ? undefined : filterStatus.value }))
const visible = ref(false), current = ref(null), action = ref('dismiss'), reason = ref(''), saving = ref(false), actionError = ref('')
const gate = useRequestGate(() => { visible.value = false; current.value = null })
function openHandle(row) { current.value = row; action.value = 'dismiss'; reason.value = ''; actionError.value = ''; visible.value = true }
function inspectMaterial() { router.push({ path: '/admin/materials', query: { materialId: current.value.material_id } }) }
async function submit() {
  if (saving.value || !current.value) return
  if (!reason.value.trim()) { actionError.value = '请填写处理原因'; return }
  const ticket = gate.begin(); saving.value = true; actionError.value = ''
  try { await handleReport(current.value.report_id, { action: action.value, reason: reason.value.trim() }); if (!gate.valid(ticket)) return; ElMessage.success('处理结果已保存'); visible.value = false; fetchList() }
  catch (err) { if (gate.valid(ticket) && !err.sessionChanged) actionError.value = err.message || '处理失败，请重试' }
  finally { if (gate.valid(ticket)) saving.value = false }
}
onMounted(fetchList)
</script>
<style scoped>.status-filter { display: flex; max-width: 100%; overflow-x: auto; margin-bottom: 20px; } [role=alert] { color: var(--color-danger); }</style>
