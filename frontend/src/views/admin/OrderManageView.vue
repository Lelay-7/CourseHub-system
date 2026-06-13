<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="订单管理" subtitle="查看并处理用户兑换订单" />
      <el-radio-group v-model="filterStatus" @change="fetchList" style="margin-bottom:16px">
        <el-radio-button :value="undefined">全部</el-radio-button>
        <el-radio-button :value="1">待处理</el-radio-button>
        <el-radio-button :value="2">处理中</el-radio-button>
        <el-radio-button :value="3">已完成</el-radio-button>
        <el-radio-button :value="4">已取消</el-radio-button>
      </el-radio-group>
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="order_id" label="ID" width="60" />
        <el-table-column prop="order_no" label="订单号" width="150" />
        <el-table-column prop="user_id" label="用户ID" width="70" />
        <el-table-column prop="goods_name" label="商品" />
        <el-table-column prop="score_amount" label="积分" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 3 ? 'success' : row.status === 4 ? 'info' : 'warning'" size="small">
              {{ row.status === 1 ? '待处理' : row.status === 2 ? '处理中' : row.status === 3 ? '已完成' : row.status === 4 ? '已取消' : '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="create_time" label="下单时间" width="160" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button v-if="row.status === 1 || row.status === 2" size="small" type="primary" @click="handleComplete(row.order_id)">完成订单</el-button>
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
import { listAdminOrders, completeOrder } from '@/api/admin'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const loading = ref(false)
const filterStatus = ref(undefined)

async function fetchList() {
  loading.value = true
  try {
    const params = filterStatus.value !== undefined ? { status: filterStatus.value } : {}
    const res = await listAdminOrders(params)
    list.value = res.data?.list || []
  } finally { loading.value = false }
}

async function handleComplete(id) {
  await completeOrder(id)
  ElMessage.success('订单已完成')
  fetchList()
}

onMounted(fetchList)
</script>
