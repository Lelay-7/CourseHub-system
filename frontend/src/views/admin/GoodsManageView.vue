<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="商品管理" subtitle="管理积分商城兑换商品" />
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="keyword" placeholder="搜索商品名称" clearable @clear="fetchList" @keyup.enter="fetchList" />
          <el-select v-model="filterStatus" placeholder="商品状态" clearable @change="fetchList">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </div>
        <el-button type="primary" @click="openDialog()">新增商品</el-button>
      </div>
      <Card>
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="goods_id" label="ID" width="60" />
        <el-table-column prop="goods_name" label="商品名称" />
        <el-table-column label="分类" width="80">
          <template #default="{ row }">{{ row.category === 1 ? '实物' : row.category === 2 ? '虚拟' : '服务' }}</template>
        </el-table-column>
        <el-table-column prop="price_score" label="积分" width="80" />
        <el-table-column prop="stock" label="库存" width="70" />
        <el-table-column prop="exchange_limit" label="限兑" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="handleStatus(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.goods_id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && list.length === 0" description="暂无数据" />
      </Card>

      <el-dialog v-model="showDialog" :title="editingId ? '编辑商品' : '新增商品'" width="450px">
        <el-form :model="form" label-width="80px">
          <el-form-item label="商品名称"><el-input v-model="form.goods_name" /></el-form-item>
          <el-form-item label="分类">
            <el-select v-model="form.category">
              <el-option :value="1" label="实物" /><el-option :value="2" label="虚拟" /><el-option :value="3" label="服务" />
            </el-select>
          </el-form-item>
          <el-form-item label="所需积分"><el-input-number v-model="form.price_score" :min="1" /></el-form-item>
          <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
          <el-form-item label="限兑次数"><el-input-number v-model="form.exchange_limit" :min="0" /></el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
          </el-form-item>
          <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showDialog = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存</el-button>
        </template>
      </el-dialog>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listGoods, createGoods, updateGoods, updateGoodsStatus, deleteGoods } from '@/api/admin'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import Card from '@/components/Card.vue'

const list = ref([])
const loading = ref(false)
const showDialog = ref(false)
const editingId = ref(null)
const keyword = ref('')
const filterStatus = ref(undefined)
const form = reactive({ goods_name: '', category: 1, price_score: 0, stock: 0, exchange_limit: 0, status: 1, description: '' })

async function fetchList() {
  loading.value = true
  try {
    const res = await listGoods({ keyword: keyword.value.trim() || undefined, status: filterStatus.value, page: 1, pageSize: 50 })
    list.value = res.data?.list || []
  }
  finally { loading.value = false }
}

function openDialog(row) {
  if (row) {
    editingId.value = row.goods_id
    form.goods_name = row.goods_name
    form.category = row.category
    form.price_score = row.price_score
    form.stock = row.stock
    form.exchange_limit = row.exchange_limit ?? 0
    form.status = row.status
    form.description = row.description || ''
  } else {
    editingId.value = null
    form.goods_name = ''; form.category = 1; form.price_score = 0; form.stock = 0; form.exchange_limit = 0; form.status = 1; form.description = ''
  }
  showDialog.value = true
}

async function handleSave() {
  if (!form.goods_name.trim()) { ElMessage.warning('请输入商品名称'); return }
  if (Number(form.price_score) < 0) { ElMessage.warning('积分不能小于 0'); return }
  if (Number(form.stock) < 0) { ElMessage.warning('库存不能小于 0'); return }
  if (Number(form.exchange_limit) < 0) { ElMessage.warning('限兑次数不能小于 0'); return }
  try {
    if (editingId.value) {
      await updateGoods(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await createGoods(form)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    fetchList()
  } catch { /* handled */ }
}

async function handleStatus(row) {
  const status = row.status === 1 ? 0 : 1
  await updateGoodsStatus(row.goods_id, status)
  ElMessage.success(status === 1 ? '商品已上架' : '商品已下架')
  fetchList()
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除该商品？', '确认', { type: 'warning' })
    await deleteGoods(id)
    ElMessage.success('已删除')
    fetchList()
  } catch { /* cancelled */ }
}

onMounted(fetchList)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.filters { display: flex; gap: 12px; }
.filters .el-input { width: 240px; }
.filters .el-select { width: 130px; }
</style>
