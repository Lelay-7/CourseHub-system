<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="Banner 管理" subtitle="管理首页轮播图" />
      <div class="toolbar">
        <span></span>
        <el-button type="primary" @click="showDialog = true">新增 Banner</el-button>
      </div>
      <Card>
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="banner_id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="image_url" label="图片URL" />
        <el-table-column prop="link_url" label="跳转链接" />
        <el-table-column prop="sort_order" label="排序" width="70" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="editRow(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.banner_id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && list.length === 0" description="暂无数据" />
      </Card>

      <el-dialog v-model="showDialog" :title="editingId ? '编辑' : '新增'" width="400px">
        <el-form :model="form" label-width="80px">
          <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
          <el-form-item label="图片URL"><el-input v-model="form.image_url" /></el-form-item>
          <el-form-item label="跳转链接"><el-input v-model="form.link_url" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sort_order" :min="0" /></el-form-item>
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
import { listBanners, createBanner, updateBanner, deleteBanner } from '@/api/admin'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import Card from '@/components/Card.vue'

const list = ref([])
const loading = ref(false)
const showDialog = ref(false)
const editingId = ref(null)
const form = reactive({ title: '', image_url: '', link_url: '', sort_order: 0, status: 1 })

async function fetchList() { loading.value = true; try { const res = await listBanners(); list.value = res.data?.list || res.data || [] } finally { loading.value = false } }
function editRow(row) { editingId.value = row.banner_id; Object.assign(form, row); showDialog.value = true }
async function handleSave() {
  try {
    if (editingId.value) { await updateBanner(editingId.value, form); ElMessage.success('更新成功') }
    else { await createBanner(form); ElMessage.success('创建成功') }
    showDialog.value = false; editingId.value = null
    form.title = ''; form.image_url = ''; form.link_url = ''; form.sort_order = 0
    fetchList()
  } catch { /* ignored */ }
}
async function handleDelete(id) {
  try { await ElMessageBox.confirm('确定删除？', '确认', { type: 'warning' }); await deleteBanner(id); ElMessage.success('已删除'); fetchList() }
  catch { /* cancelled */ }
}
onMounted(fetchList)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
