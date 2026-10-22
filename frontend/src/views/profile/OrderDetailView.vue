<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="订单详情" :show-back="true" />

      <div v-if="loading" class="detail-loading">
        <LoadingSkeleton type="list" :rows="5" />
      </div>

      <ErrorState v-else-if="loadError" :title="loadError" @retry="fetchDetail" />

      <template v-else-if="order.order_id">
        <div class="detail-card card">
          <div v-if="order.status !== 4" class="order-status-bar">
            <el-steps :active="statusStep" align-center>
              <el-step title="待处理" />
              <el-step title="处理中" />
              <el-step title="已完成" />
            </el-steps>
          </div>

          <el-descriptions :column="1" border class="order-info">
            <el-descriptions-item label="订单编号">{{ order.order_no }}</el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <el-tag :type="statusTag">{{ ORDER_STATUS_LABEL[order.status] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="订单内容">{{ order.goods_name || '订单' }}</el-descriptions-item>
            <el-descriptions-item label="积分金额">
              <span class="order-amount">
                <el-icon><StarFilled /></el-icon> {{ order.score_amount }} 积分
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="交付说明">{{ fulfillmentNote }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(order.create_time) }}</el-descriptions-item>
          </el-descriptions>

          <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
          <div class="order-actions">
            <el-button @click="$router.push('/mall')">返回商城</el-button>
            <el-button
              v-if="order.status === 1"
              type="danger"
              :disabled="!!pending"
              :loading="pending === 'cancel' && !confirming"
              @click="handleCancel"
            >
              取消订单
            </el-button>
            <el-button
              v-if="order.status === 3 || order.status === 4"
              type="info"
              plain
              :disabled="!!pending"
              :loading="pending === 'delete' && !confirming"
              @click="handleDelete"
            >
              删除记录
            </el-button>
          </div>
        </div>
      </template>

      <ErrorState v-else title="订单不存在" @retry="fetchDetail" />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { StarFilled } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'
import { ORDER_STATUS_LABEL } from '@/utils/constants'
import { useUserStore } from '@/stores/user'
import { useRequestGate } from '@/composables/useListQuery'
import * as orderApi from '@/api/order'

const route = useRoute()
const userStore = useUserStore()
const router = useRouter()

const order = ref({})
const loading = ref(true)
const loadError = ref('')
const actionError = ref('')
const pending = ref('')
const confirming = ref(false)
const gate = useRequestGate(() => {
  closeConfirmation()
  order.value = {}; pending.value = ''; actionError.value = ''; loading.value = false
  loadError.value = '登录状态已改变，请重新加载订单'
})
function closeConfirmation() {
  if (confirming.value) { confirming.value = false; ElMessageBox.close() }
}
onBeforeUnmount(closeConfirmation)
const fulfillmentNote = computed(() => order.value.fulfillment_note || (
  order.value.status === 4 ? '订单已取消，积分已退回'
    : order.value.status === 3 ? '历史订单未记录交付说明' : '等待管理员填写领取或发放安排'
))

const statusStep = computed(() => {
  const s = order.value.status
  if (s === 1) return 0
  if (s === 2) return 1
  if (s === 3) return 2
  return 0
})
const statusTag = computed(() => {
  const map = { 1: 'warning', 2: 'success', 3: '', 4: 'info' }
  return map[order.value.status] || ''
})

async function fetchDetail() {
  const id = String(route.params.id || ''), ticket = gate.begin()
  closeConfirmation()
  order.value = {}; loadError.value = ''; actionError.value = ''; pending.value = ''
  loading.value = true
  if (!id) { loading.value = false; return }
  const owns = () => gate.valid(ticket) && id === String(route.params.id || '')
  try {
    const res = await orderApi.getOrderDetail(id)
    if (!owns()) return
    if (res.data?.order_id && String(res.data.order_id) === id) order.value = res.data
    else loadError.value = '订单不存在或不可访问'
  } catch (error) {
    if (owns() && !error.sessionChanged) loadError.value = error.response?.status === 404 ? '订单不存在或不可访问' : '订单加载失败，请重试'
  } finally { if (owns()) loading.value = false }
}

async function handleCancel() {
  return mutateOrder('cancel')
}

async function handleDelete() {
  return mutateOrder('delete')
}

async function mutateOrder(kind) {
  if (pending.value || loading.value || !order.value.order_id) return
  if (kind === 'cancel' ? order.value.status !== 1 : ![3, 4].includes(order.value.status)) return
  const id = String(order.value.order_id), ticket = gate.begin()
  const owns = () => gate.valid(ticket) && String(route.params.id) === id && String(order.value.order_id) === id
  pending.value = kind; confirming.value = true; actionError.value = ''
  try {
    try {
      await ElMessageBox.confirm(kind === 'cancel' ? '确认取消该订单吗？积分将退回' : '确认删除该记录吗？删除后不可恢复', '操作确认', {
        confirmButtonText: kind === 'cancel' ? '确认' : '确认删除', cancelButtonText: kind === 'cancel' ? '再想想' : '取消', type: 'warning'
      })
    } catch { return }
    if (!owns()) return
    confirming.value = false
    if (kind === 'cancel') {
      const res = await orderApi.cancelOrder(id)
      if (!owns()) return
      const balance = Number(res.data?.score_balance)
      if (res.data?.score_balance !== undefined && Number.isFinite(balance)) userStore.updateScoreBalance(balance)
      order.value.status = 4
      order.value.fulfillment_note = '订单已取消，积分已退回'
      ElMessage.success('订单已取消，积分已退回')
    } else {
      await orderApi.deleteOrder(id)
      if (!owns()) return
      ElMessage.success('记录已删除')
      await router.replace('/profile/orders')
    }
  } catch (error) {
    if (owns() && !error.sessionChanged) actionError.value = kind === 'cancel' ? '取消失败，订单未更新，请重试' : '删除失败，记录已保留，请重试'
  } finally { if (gate.valid(ticket)) { pending.value = ''; confirming.value = false } }
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

watch(() => route.params.id, fetchDetail, { immediate: true })
</script>

<style scoped>
.detail-loading { padding: var(--spacing-lg); }
.detail-card { }
.order-status-bar { margin-bottom: var(--spacing-lg); padding: var(--spacing-md); }
.order-info { margin-bottom: var(--spacing-md); }
.order-amount { display: inline-flex; align-items: center; gap: 4px; color: var(--color-warning); font-weight: 600; }
.order-actions { display: flex; gap: var(--spacing-sm); justify-content: flex-end; margin-top: var(--spacing-md); }
.action-error { color: var(--color-danger); margin-top: var(--spacing-md); line-height: 1.6; }
@media (max-width: 767px) { .order-actions { flex-wrap: wrap; } }
</style>
