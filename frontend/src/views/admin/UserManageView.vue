<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="用户管理" subtitle="查看、启用/禁用用户账号" />
      <div class="filters">
        <el-input v-model="keyword" placeholder="搜索用户名、姓名、学号、邮箱、手机号" clearable @clear="resetAndFetch" @keyup.enter="resetAndFetch" />
        <el-select v-model="filterStatus" placeholder="账号状态" clearable @change="resetAndFetch">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </div>
      <el-radio-group v-model="filterType" @change="resetAndFetch" style="margin-bottom:16px">
        <el-radio-button :value="undefined">全部</el-radio-button>
        <el-radio-button :value="1">学生</el-radio-button>
        <el-radio-button :value="2">教师</el-radio-button>
        <el-radio-button :value="3">管理员</el-radio-button>
      </el-radio-group>
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="user_id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="real_name" label="真实姓名" />
        <el-table-column prop="student_no" label="学号/工号" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column label="角色" width="80">
          <template #default="{ row }">
            <el-tag :type="row.user_type === 3 ? 'danger' : row.user_type === 2 ? 'warning' : ''">
              {{ row.user_type === 3 ? '管理员' : row.user_type === 2 ? '教师' : '学生' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="院系" />
        <el-table-column prop="score_balance" label="积分" width="70" />
        <el-table-column prop="create_time" label="注册时间" width="170">
          <template #default="{ row }">{{ formatTime(row.create_time) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="handleToggle(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && list.length === 0" description="暂无数据" />
      <el-pagination
        v-if="total > pageSize"
        background
        layout="prev, pager, next"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        @current-change="handlePageChange"
      />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, toggleUserStatus } from '@/api/admin'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const loading = ref(false)
const filterType = ref(undefined)
const filterStatus = ref(undefined)
const keyword = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)
async function fetchList() {
  loading.value = true
  try {
    const res = await listUsers({
      userType: filterType.value,
      status: filterStatus.value,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: pageSize
    })
    list.value = res.data?.list || []
    total.value = Number(res.data?.total || 0)
  } finally { loading.value = false }
}
function resetAndFetch() { page.value = 1; fetchList() }
function handlePageChange(value) { page.value = value; fetchList() }
async function handleToggle(row) {
  try {
    const action = row.status === 1 ? '禁用' : '启用'
    await ElMessageBox.confirm(`确定${action}用户 "${row.nickname}"？`, '确认', { type: 'warning' })
    const newStatus = row.status === 1 ? 0 : 1
    await toggleUserStatus(row.user_id, newStatus)
    ElMessage.success(`${action}成功`)
    fetchList()
  } catch { /* cancelled */ }
}
function formatTime(time) { return time ? new Date(time).toLocaleString('zh-CN') : '-' }
onMounted(fetchList)
</script>

<style scoped>
.filters { display: flex; gap: 12px; margin-bottom: 12px; }
.filters .el-input { max-width: 360px; }
.filters .el-select { width: 140px; }
.el-pagination { justify-content: center; margin-top: 16px; }
</style>
