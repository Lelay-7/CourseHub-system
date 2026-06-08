<template>
  <AppLayout>
    <div class="page-content">
      <PageHeader title="订单详情" :show-back="true" />

      <div v-if="loading" class="detail-loading">
        <LoadingSkeleton type="list" :rows="5" />
      </div>

      <template v-else-if="order.order_id">
        <div class="detail-card card">
          <div class="order-status-bar">
            <el-steps :active="statusStep" align-center>
              <el-step title="待支付" />
              <el-step title="已支付" />
              <el-step title="已完成" />
            </el-steps>
          </div>

          <el-descriptions :column="1" border class="order-info">
            <el-descriptions-item label="订单编号">{{ order.order_no }}</el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <el-tag :type="statusTag">{{ ORDER_STATUS_LABEL[order.status] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="订单内容">{{ order.subject || order.description }}</el-descriptions-item>
            <el-descriptions-item label="积分金额">
              <span class="order-amount">
                <el-icon><StarFilled /></el-icon> {{ order.score_amount }} 积分
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(order.create_time) }}</el-descriptions-item>
          </el-descriptions>

          <div class="order-actions">
            <el-button
              v-if="order.status === 1"
              type="danger"
              @click="handleCancel"
            >
              取消订单
            </el-button>
            <el-button
              v-if="order.status === 3 || order.status === 4"
              type="info"
              plain
              @click="handleDelete"
            >
              删除订单
            </el-button>
          </div>
        </div>
      </template>

      <ErrorState v-else title="订单不存在" @retry="fetchDetail" />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { StarFilled } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'
import { ORDER_STATUS_LABEL } from '@/utils/constants'
import * as orderApi from '@/api/order'

const route = useRoute()
const router = useRouter()

const order = ref({})
const loading = ref(true)

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
  loading.value = true
  try {
    const res = await orderApi.getOrderDetail(route.params.id)
    if (res.code === 200) order.value = res.data || {}
  } catch { /* handled */ }
  finally { loading.value = false }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确认取消该订单吗？积分将退回', '操作确认', {
      confirmButtonText: '确认', cancelButtonText: '再想想', type: 'warning'
    })
  } catch { return }
  try {
    await orderApi.cancelOrder(order.value.order_id)
    ElMessage.success('订单已取消，积分已退回')
    order.value.status = 4
  } catch { /* handled */ }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确认删除该订单吗？删除后不可恢复', '操作确认', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  ElMessage.success('订单已删除')
  router.back()
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(fetchDetail)
</script>

<style scoped>
.detail-loading { padding: var(--spacing-lg); }
.detail-card { }
.order-status-bar { margin-bottom: var(--spacing-lg); padding: var(--spacing-md); }
.order-info { margin-bottom: var(--spacing-md); }
.order-amount { display: inline-flex; align-items: center; gap: 4px; color: var(--color-warning); font-weight: 600; }
.order-actions { display: flex; gap: var(--spacing-sm); justify-content: flex-end; margin-top: var(--spacing-md); }
</style>
