<template>
  <AppLayout><PageContainer>
    <PageHeader title="订单管理" subtitle="查看兑换内容，并向用户提供领取、发放或服务安排。" />
    <el-radio-group v-model="filterStatus" @change="resetAndFetch" class="status-filter"><el-radio-button value="">全部</el-radio-button><el-radio-button v-for="(label, id) in ORDER_STATUS_LABEL" :key="id" :value="Number(id)">{{ label }}</el-radio-button></el-radio-group>
    <ErrorState v-if="error" :title="error" @retry="fetchList" />
    <el-table v-else :data="list" border stripe v-loading="loading">
      <el-table-column prop="order_no" label="订单号" min-width="180" /><el-table-column prop="user_id" label="用户 ID" width="90" /><el-table-column prop="goods_name" label="商品" min-width="180" /><el-table-column prop="score_amount" label="积分" width="90" />
      <el-table-column label="状态" width="110"><template #default="{ row }">{{ ORDER_STATUS_LABEL[row.status] }}</template></el-table-column>
      <el-table-column label="操作" width="130" fixed="right"><template #default="{ row }"><el-button size="small" @click="openOrder(row.order_id)">详情与处理</el-button></template></el-table-column>
    </el-table>
    <ListPagination :page="page" :page-size="pageSize" :total="total" @change="handlePageChange" />
    <el-dialog v-model="visible" title="兑换订单详情" width="min(620px, 94vw)" :close-on-click-modal="!saving" :show-close="!saving">
      <ErrorState v-if="detailError && !detail" :title="detailError" @retry="openOrder(selectedId)" />
      <div v-else v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="1" border>
          <el-descriptions-item label="订单号">{{ detail.order_no }}</el-descriptions-item>
          <el-descriptions-item label="兑换内容">{{ detail.goods_name }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.user_id }}</el-descriptions-item>
          <el-descriptions-item label="积分">{{ detail.score_amount }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ ORDER_STATUS_LABEL[detail.status] }}</el-descriptions-item>
          <el-descriptions-item label="处理说明">{{ detail.fulfillment_note || '尚未填写' }}</el-descriptions-item>
        </el-descriptions>
        <el-form v-if="detail && [1, 2].includes(detail.status)" label-position="top" style="margin-top:20px">
          <el-form-item label="交付说明（用户可见）"><el-input v-model="note" :disabled="saving" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="填写领取地点、时间、虚拟权益发放情况或服务安排" /></el-form-item>
        </el-form>
        <p v-if="detailError" role="alert">{{ detailError }}</p>
      </div>
      <template #footer><el-button :disabled="saving" @click="visible = false">关闭</el-button><el-button v-if="detail && [1, 2].includes(detail.status)" type="primary" :loading="saving" @click="processOrder">确认完成</el-button></template>
    </el-dialog>
  </PageContainer></AppLayout>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/admin'
import { ORDER_STATUS_LABEL } from '@/utils/constants'
import { usePagedList, useRequestGate } from '@/composables/useListQuery'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import ErrorState from '@/components/ErrorState.vue'
import ListPagination from '@/components/ListPagination.vue'
const filterStatus = ref('')
const { list, loading, error, page, pageSize, total, fetchList, resetAndFetch, handlePageChange } = usePagedList(api.listAdminOrders, () => ({ status: filterStatus.value === '' ? undefined : filterStatus.value }))
const visible = ref(false), detail = ref(null), detailLoading = ref(false), detailError = ref(''), saving = ref(false), selectedId = ref(null), note = ref('')
const gate = useRequestGate(() => { visible.value = false; detail.value = null })
async function openOrder(id) {
  const ticket = gate.begin()
  visible.value = true; selectedId.value = id; detail.value = null; detailLoading.value = true; detailError.value = ''
  try { const res = await api.getAdminOrder(id); if (gate.valid(ticket)) { detail.value = res.data; note.value = res.data?.fulfillment_note || '' } }
  catch (err) { if (gate.valid(ticket) && !err.sessionChanged) detailError.value = '订单详情加载失败' }
  finally { if (gate.valid(ticket)) detailLoading.value = false }
}
async function processOrder() {
  if (saving.value || !detail.value) return
  if (!note.value.trim()) { detailError.value = '请填写用户可见的交付说明'; return }
  const ticket = gate.begin()
  saving.value = true; detailError.value = ''
  try {
    await api.completeOrder(detail.value.order_id, note.value.trim())
    if (!gate.valid(ticket)) return
    ElMessage.success('订单处理结果已保存'); visible.value = false; fetchList()
  } catch (err) { if (gate.valid(ticket) && !err.sessionChanged) detailError.value = err.message || '处理失败，请重试' }
  finally { if (gate.valid(ticket)) saving.value = false }
}
onMounted(fetchList)
</script>
<style scoped>.status-filter { display: flex; max-width: 100%; overflow-x: auto; margin-bottom: 20px; } [role=alert] { color: var(--color-danger); }</style>
