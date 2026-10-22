<template>
  <AppLayout><PageContainer>
    <PageHeader title="资料审核" subtitle="检查提交文件和修改历史，再作出审核结论。" />
    <el-radio-group v-model="filterStatus" @change="resetAndFetch" class="status-filter">
      <el-radio-button value="">全部</el-radio-button><el-radio-button :value="0">待审核</el-radio-button><el-radio-button :value="1">已发布</el-radio-button><el-radio-button :value="2">已驳回</el-radio-button><el-radio-button :value="3">已下架</el-radio-button>
    </el-radio-group>
    <ErrorState v-if="error" :title="error" @retry="fetchList" />
    <el-table v-else :data="list" border stripe v-loading="loading">
      <el-table-column prop="material_id" label="ID" width="70" />
      <el-table-column prop="material_name" label="资料名称" min-width="200" />
      <el-table-column prop="uploader_name" label="上传者" min-width="120" />
      <el-table-column prop="revision" label="版本" width="70" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'">{{ statusName(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" fixed="right" width="140"><template #default="{ row }"><el-button size="small" type="primary" plain @click="inspect(row.material_id)">检查与处理</el-button></template></el-table-column>
    </el-table>
    <ListPagination :page="page" :page-size="pageSize" :total="total" @change="handlePageChange" />
    <el-drawer v-model="visible" title="资料审核详情" size="min(760px, 100%)" :close-on-click-modal="!acting" :close-on-press-escape="!acting" :show-close="!acting" @closed="detailGate.invalidate()">
      <div v-loading="detailLoading">
        <ErrorState v-if="detailError && !detail" :title="detailError" @retry="inspect(selectedId)" />
        <template v-if="detail">
          <h2>{{ detail.material_name }}</h2>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="上传者">{{ detail.uploader_name || detail.uploader_id }}</el-descriptions-item>
            <el-descriptions-item label="状态 / 版本">{{ statusName(detail.status) }} · V{{ detail.revision }}</el-descriptions-item>
            <el-descriptions-item label="文件">{{ detail.original_file_name || detail.material_name }}</el-descriptions-item>
            <el-descriptions-item label="关联课程">{{ detail.course_name || (detail.course_id ? `课程 #${detail.course_id}` : '未关联') }}</el-descriptions-item>
            <el-descriptions-item label="下载积分">{{ detail.download_score }}</el-descriptions-item>
            <el-descriptions-item label="关键词">{{ detail.keyword || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="说明">{{ detail.description || '未填写' }}</el-descriptions-item>
          </el-descriptions>
          <div class="review-actions"><el-button type="primary" :loading="fileLoading" :disabled="!detail.has_file" @click="downloadVersion(detail.revision)">下载当前文件检查</el-button><el-button :disabled="acting" @click="inspect(selectedId)">刷新详情</el-button></div>
          <p class="hint">审核检查不扣积分，也不增加普通下载次数。请实际检查文件内容后再通过。</p>
          <section v-if="detail.status === 0 || detail.status === 1" class="review-decision">
            <h3>处理意见</h3>
            <el-input v-model="reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="驳回或下架时必填，请说明需要修改的内容" :disabled="acting" />
            <div class="review-actions">
              <el-button v-if="detail.status === 0" type="success" :disabled="!inspected" :loading="acting" @click="decide('approve')">通过当前版本</el-button>
              <el-button v-if="detail.status === 0" type="danger" :loading="acting" @click="decide('reject')">驳回并反馈原因</el-button>
              <el-button v-if="detail.status === 1" type="danger" :loading="acting" @click="decide('deactivate')">下架资料</el-button>
            </div>
          </section>
          <p v-if="detailError" class="action-error" role="alert">{{ detailError }}；可刷新详情后再次检查。</p>
          <h3>提交版本</h3>
          <div v-for="version in detail.revisions || []" :key="version.revision" class="history-row">
            <div><strong>V{{ version.revision }} · {{ version.material_name }}</strong><p>{{ version.original_file_name }} · {{ formatTime(version.create_time) }}</p></div>
            <el-button :disabled="fileLoading || version.has_file === false" @click="downloadVersion(version.revision)">检查文件</el-button>
          </div>
          <h3>审核记录</h3>
          <p v-if="!detail.audit_history?.length" class="hint">尚无审核记录</p>
          <div v-for="(event, i) in detail.audit_history || []" :key="i" class="history-row"><div><strong>V{{ event.revision }} · {{ actionName(event.action) }}</strong><p>{{ event.reason || '审核通过' }}</p><small>{{ event.reviewer_name || '管理员' }} · {{ formatTime(event.create_time) }}</small></div></div>
        </template>
      </div>
    </el-drawer>
  </PageContainer></AppLayout>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as api from '@/api/admin'
import { saveBlobResponse } from '@/api/material'
import { usePagedList, useRequestGate } from '@/composables/useListQuery'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import ErrorState from '@/components/ErrorState.vue'
import ListPagination from '@/components/ListPagination.vue'
const filterStatus = ref(0)
const { list, loading, error, page, pageSize, total, fetchList, resetAndFetch, handlePageChange } = usePagedList(api.listMaterials, () => ({ status: filterStatus.value === '' ? undefined : filterStatus.value }))
const visible = ref(false), detailLoading = ref(false), detail = ref(null), detailError = ref(''), selectedId = ref(null), reason = ref(''), inspected = ref(false), acting = ref(false), fileLoading = ref(false)
const detailGate = useRequestGate(() => { visible.value = false; detail.value = null; acting.value = false })
function statusName(status) { return ['待审核','已发布','已驳回','已下架'][status] || '未知' }
function actionName(action) { return ({ approve: '审核通过', reject: '驳回', deactivate: '下架' })[action] || action }
function formatTime(time) { return time ? new Date(time).toLocaleString('zh-CN') : '' }
async function inspect(id) {
  if (acting.value) return
  const ticket = detailGate.begin()
  selectedId.value = id; visible.value = true; detailLoading.value = true; detail.value = null; detailError.value = ''; reason.value = ''; inspected.value = false
  try { const res = await api.getAdminMaterial(id); if (detailGate.valid(ticket)) detail.value = res.data }
  catch (err) { if (detailGate.valid(ticket) && !err.sessionChanged) detailError.value = err.message || '详情加载失败' }
  finally { if (detailGate.valid(ticket)) detailLoading.value = false }
}
async function downloadVersion(revision) {
  if (fileLoading.value || !detail.value) return
  const id = detail.value.material_id
  fileLoading.value = true
  try {
    const res = await api.getAdminMaterialFile(id, revision)
    if (!visible.value || detail.value?.material_id !== id) return
    saveBlobResponse(res, `资料-${id}-V${revision}`)
    if (revision === detail.value.revision) inspected.value = true
  } catch (err) { if (!err.sessionChanged) detailError.value = '文件获取失败，请重试' }
  finally { fileLoading.value = false }
}
async function decide(action) {
  if (acting.value || !detail.value) return
  if (action !== 'approve' && !reason.value.trim()) { detailError.value = '请填写处理原因'; return }
  if (action === 'approve' && !inspected.value) return
  const ticket = detailGate.begin(), current = detail.value
  acting.value = true; detailError.value = ''
  try {
    if (action === 'approve') await api.approveMaterial(current.material_id, current.revision)
    else if (action === 'reject') await api.rejectMaterial(current.material_id, reason.value.trim(), current.revision)
    else await api.deactivateMaterial(current.material_id, reason.value.trim())
    if (!detailGate.valid(ticket)) return
    ElMessage.success('处理结果已保存'); visible.value = false; fetchList()
  } catch (err) { if (detailGate.valid(ticket) && !err.sessionChanged) detailError.value = err.message || '处理失败' }
  finally { if (detailGate.valid(ticket)) acting.value = false }
}
const route = useRoute()
onMounted(() => { fetchList(); if (Number(route.query.materialId)) inspect(Number(route.query.materialId)) })
</script>
<style scoped>
.status-filter { display: flex; max-width: 100%; overflow-x: auto; margin-bottom: 20px; }
.review-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 16px; }
.hint, .history-row p, .history-row small { color: var(--color-text-secondary); }
.review-decision { margin: 24px 0; padding: 16px; background: var(--color-bg-soft); border-radius: 12px; }
.history-row { display: flex; justify-content: space-between; gap: 12px; padding: 16px 0; border-bottom: 1px solid var(--color-border); overflow-wrap: anywhere; }
.action-error { color: var(--color-danger); }
</style>
