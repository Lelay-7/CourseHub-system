<template>
  <AppLayout>
    <div class="page-content">
      <PageHeader title="我的订单" :show-back="true" />

      <!-- 状态筛选 -->
      <el-radio-group v-model="statusFilter" size="small" @change="fetchList(true)" class="status-filter">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button :value="1">待支付</el-radio-button>
        <el-radio-button :value="2">已支付</el-radio-button>
        <el-radio-button :value="3">已完成</el-radio-button>
        <el-radio-button :value="4">已取消</el-radio-button>
      </el-radio-group>

      <div v-if="orderList.length > 0" class="order-list">
        <div v-for="order in orderList" :key="order.order_id" class="order-card card" @click="goDetail(order.order_id)">
          <div class="order-header flex-between">
            <span class="order-id">订单号：{{ order.order_no }}</span>
            <el-tag :type="statusTag(order.status)" size="small">
              {{ ORDER_STATUS_LABEL[order.status] || '未知' }}
            </el-tag>
          </div>
          <div class="order-body">
            <div class="order-subject">
              <strong>{{ order.subject || order.description }}</strong>
              <small>积分商城兑换</small>
            </div>
            <span class="order-amount">
              <el-icon><StarFilled /></el-icon> {{ order.score_amount }} 积分
            </span>
          </div>
          <div class="order-footer flex-between">
            <span class="order-time">{{ formatTime(order.create_time) }}</span>
            <el-button
              v-if="order.status === 1"
              type="danger"
              size="small"
              plain
              @click.stop="handleCancel(order)"
            >
              取消订单
            </el-button>
          </div>
        </div>
      </div>

      <EmptyState v-else-if="!loading" description="暂无订单" />
      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon> 加载中...
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, StarFilled } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { ORDER_STATUS_LABEL } from '@/utils/constants'
import * as orderApi from '@/api/order'

const router = useRouter()
const orderList = ref([])
const loading = ref(false)
const page = ref(1)
const noMore = ref(false)
const statusFilter = ref('')

function statusTag(status) {
  const map = { 1: 'warning', 2: 'success', 3: '', 4: 'info' }
  return map[status] || ''
}

async function fetchList(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; orderList.value = []; noMore.value = false }
  loading.value = true
  try {
    const res = await orderApi.getOrderList({
      page: page.value, pageSize: 20,
      status: statusFilter.value || undefined
    })
    if (res.code === 200) {
      const data = res.data || {}
      const list = data.list || data.records || []
      if (reset) orderList.value = list
      else orderList.value.push(...list)
      noMore.value = orderList.value.length >= (data.total || 0)
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

async function handleCancel(order) {
  try {
    await ElMessageBox.confirm('确认取消该订单吗？取消后积分将退回', '操作确认', {
      confirmButtonText: '确认取消', cancelButtonText: '再想想', type: 'warning'
    })
  } catch { return }
  try {
    await orderApi.cancelOrder(order.order_id)
    ElMessage.success('订单已取消，积分已退回')
    order.status = 4
  } catch { /* handled */ }
}

function goDetail(id) {
  router.push(`/profile/orders/${id}`)
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => { fetchList(true) })
</script>

<style scoped>
.status-filter { margin-bottom: var(--spacing-md); }

.order-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.order-card {
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}
.order-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.order-header { margin-bottom: var(--spacing-sm); }
.order-id { font-size: var(--font-size-xs); color: var(--color-text-secondary); }
.order-body {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: var(--spacing-sm);
}
.order-subject {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.order-subject strong {
  color: var(--color-text-primary);
}
.order-subject small {
  color: var(--color-text-secondary);
}
.order-amount { display: flex; align-items: center; gap: 4px; color: var(--color-warning); font-weight: 600; }
.order-time { font-size: var(--font-size-xs); color: var(--color-text-placeholder); }

.load-more { text-align: center; padding: var(--spacing-md); color: var(--color-text-secondary); }

@media (max-width: 767px) {
  .status-filter {
    max-width: 100%;
    overflow-x: auto;
    display: flex;
    padding-bottom: 4px;
  }
  .order-body,
  .order-footer {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--spacing-sm);
  }
}
</style>
