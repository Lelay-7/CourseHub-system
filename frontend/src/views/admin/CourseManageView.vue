<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="课程管理" subtitle="新增、编辑、删除课程信息" />
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索课程名称/代码" clearable @clear="fetchList" @keyup.enter="fetchList" style="width:300px" />
        <el-button type="primary" @click="showDialog = true">新增课程</el-button>
      </div>
      <Card>
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="course_id" label="ID" width="60" />
        <el-table-column prop="course_name" label="课程名称" />
        <el-table-column prop="course_code" label="代码" width="100" />
        <el-table-column prop="credit" label="学分" width="70" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ row.course_type === 1 ? '必修' : row.course_type === 2 ? '选修' : '通识' }}</template>
        </el-table-column>
        <el-table-column label="课程状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button size="small" @click="editRow(row)">编辑</el-button>
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="handleStatus(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.course_id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-if="!loading && list.length === 0" description="暂无数据" />
      </Card>

      <el-dialog v-model="showDialog" :title="editingId ? '编辑课程' : '新增课程'" width="500px">
        <el-form :model="form" label-width="80px">
          <el-form-item label="课程名称"><el-input v-model="form.course_name" /></el-form-item>
          <el-form-item label="课程代码"><el-input v-model="form.course_code" /></el-form-item>
          <el-form-item label="课程类型">
            <el-select v-model="form.course_type">
              <el-option :value="1" label="必修" /><el-option :value="2" label="选修" /><el-option :value="3" label="通识" />
            </el-select>
          </el-form-item>
          <el-form-item label="学分"><el-input-number v-model="form.credit" :min="1" :max="8" :step="0.5" /></el-form-item>
          <el-form-item label="教师ID"><el-input-number v-model="form.teacher_id" :min="1" /></el-form-item>
          <el-form-item label="学期"><el-input v-model="form.semester" placeholder="如 2025-2026-2" /></el-form-item>
          <el-form-item label="上课时间"><el-input v-model="form.schedule" /></el-form-item>
          <el-form-item label="上课地点"><el-input v-model="form.location" /></el-form-item>
          <el-form-item label="分类ID"><el-input-number v-model="form.category_id" :min="1" /></el-form-item>
          <el-form-item label="课程状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
          </el-form-item>
          <el-form-item label="简介"><el-input v-model="form.description" type="textarea" /></el-form-item>
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
import { listCourses, createCourse, updateCourse, updateCourseStatus, deleteCourse } from '@/api/admin'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import Card from '@/components/Card.vue'

const list = ref([])
const loading = ref(false)
const keyword = ref('')
const showDialog = ref(false)
const editingId = ref(null)
const form = reactive({ course_name: '', course_code: '', course_type: 1, credit: 3, teacher_id: null, semester: '', schedule: '', location: '', category_id: null, selection_count: 0, status: 1, description: '' })

async function fetchList() {
  loading.value = true
  try { const res = await listCourses({ keyword: keyword.value, page: 1, pageSize: 50 }); list.value = res.data?.list || [] }
  finally { loading.value = false }
}
function editRow(row) { editingId.value = row.course_id; Object.assign(form, row); showDialog.value = true }
async function handleSave() {
  if (!form.course_name.trim()) { ElMessage.warning('请输入课程名称'); return }
  if (!form.course_code.trim()) { ElMessage.warning('请输入课程代码'); return }
  if (Number(form.credit) <= 0) { ElMessage.warning('学分必须大于 0'); return }
  if (Number(form.selection_count || 0) < 0) { ElMessage.warning('选课人数不能小于 0'); return }
  const payload = {
    ...form,
    course_name: form.course_name.trim(),
    course_code: form.course_code.trim()
  }
  try {
    if (editingId.value) { await updateCourse(editingId.value, payload); ElMessage.success('更新成功') }
    else { await createCourse(payload); ElMessage.success('创建成功') }
    showDialog.value = false; editingId.value = null
    form.course_name = ''; form.course_code = ''; form.course_type = 1; form.credit = 3; form.teacher_id = null; form.semester = ''; form.schedule = ''; form.location = ''; form.category_id = null; form.selection_count = 0; form.status = 1; form.description = ''
    fetchList()
  } catch { /* handled by interceptor */ }
}
async function handleStatus(row) {
  const status = row.status === 1 ? 0 : 1
  await updateCourseStatus(row.course_id, status)
  ElMessage.success(status === 1 ? '课程已上架' : '课程已下架')
  fetchList()
}
async function handleDelete(id) {
  try { await ElMessageBox.confirm('确定删除该课程？', '确认', { type: 'warning' }); await deleteCourse(id); ElMessage.success('已删除'); fetchList() }
  catch { /* cancelled */ }
}
onMounted(fetchList)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
