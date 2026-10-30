<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="Banner 管理" subtitle="管理首页轮播图" />
      <div class="toolbar">
        <span></span>
        <el-button type="primary" @click="openCreate">新增 Banner</el-button>
      </div>
      <ErrorState v-if="error" :title="error" @retry="fetchList" />
      <el-table v-else :data="list" border stripe v-loading="loading">
        <el-table-column prop="banner_id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="image_url" label="图片URL" min-width="200" show-overflow-tooltip />
        <el-table-column prop="link_url" label="跳转链接" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sort_order" label="排序" width="70" />
        <el-table-column label="操作" fixed="right" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="editRow(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.banner_id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!error && !loading && list.length === 0" description="暂无数据" />

      <el-dialog v-model="showDialog" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" :title="editingId ? '编辑' : '新增'" width="min(500px, 94vw)">
        <el-form :model="form" label-width="80px">
          <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
          <el-form-item label="图片URL"><el-input v-model="form.image_url" /></el-form-item>
          <el-form-item label="跳转链接"><el-input v-model="form.link_url" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sort_order" :min="0" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button :disabled="saving" @click="showDialog = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
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
import ErrorState from '@/components/ErrorState.vue'
import { useRequestGate } from '@/composables/useListQuery'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const loading = ref(false), error = ref('')
const gate = useRequestGate(() => { list.value = []; showDialog.value = false })
const showDialog = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({ title: '', image_url: '', link_url: '', sort_order: 0, status: 1 })

async function fetchList() {
  const ticket = gate.begin(); loading.value = true; error.value = ''
  try { const res = await listBanners(); if (gate.valid(ticket)) list.value = res.data?.list || res.data || [] }
  catch (err) { if (gate.valid(ticket) && !err.sessionChanged) error.value = '轮播加载失败' }
  finally { if (gate.valid(ticket)) loading.value = false }
}

function openCreate() { editingId.value = null; Object.keys(form).forEach(key => delete form[key]); Object.assign(form, { title: '', image_url: '', link_url: '', sort_order: 0, status: 1 }); showDialog.value = true }
function editRow(row) { editingId.value = row.banner_id; Object.assign(form, row); showDialog.value = true }
async function handleSave() {
  if (saving.value) return
  saving.value = true
  try {
    if (editingId.value) { await updateBanner(editingId.value, form); ElMessage.success('更新成功') }
    else { await createBanner(form); ElMessage.success('创建成功') }
    showDialog.value = false; editingId.value = null
    form.title = ''; form.image_url = ''; form.link_url = ''; form.sort_order = 0
    fetchList()
  } catch { /* request displays failure */ } finally { saving.value = false }
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
