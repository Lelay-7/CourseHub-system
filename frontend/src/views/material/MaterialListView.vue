<template>
  <AppLayout>
    <PageContainer compact>
      <PageHeader
        title="学习资料"
        subtitle="查找复习提纲、实验模板、课件和往年题。"
      >
        <template #extra>
          <el-radio-group v-model="listMode" size="small" style="margin-right:12px" @change="applyFilters">
            <el-radio-button value="all">全部资料</el-radio-button>
            <el-radio-button value="my">我的上传</el-radio-button>
          </el-radio-group>
          <el-button :icon="Download" @click="$router.push('/profile/downloads')">我的下载</el-button>
          <el-button type="primary" :icon="UploadFilled" @click="openUpload">上传资料</el-button>
        </template>
      </PageHeader>

      <Toolbar v-if="listMode === 'all'">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件名、上传者、关键词"
          :prefix-icon="Search"
          clearable
          @input="onSearchInput"
          @keyup.enter="applyFilters"
        />
        <el-select v-model="filters.courseId" placeholder="关联课程" clearable filterable @change="applyFilters">
          <el-option v-for="c in courseOptions" :key="c.course_id" :label="c.course_name" :value="c.course_id" />
        </el-select>
        <el-select v-model="filters.materialType" placeholder="文件类型" clearable @change="applyFilters">
          <el-option v-for="(label, val) in MATERIAL_TYPE_LABEL" :key="val" :label="label" :value="Number(val)" />
        </el-select>
        <el-radio-group v-model="priceFilter" size="small" @change="applyFilters">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="free">免费</el-radio-button>
          <el-radio-button value="score">积分</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="filters.sortBy" size="small" @change="applyFilters">
          <el-radio-button value="download_count">下载量</el-radio-button>
          <el-radio-button value="create_time">最新</el-radio-button>

        </el-radio-group>
      </Toolbar>

      <div class="score-rule">
        首次审核通过奖励 2 积分；驳回后可修改说明或替换文件重新提交。
      </div>

      <ErrorState v-if="error" :title="error" @retry="loadCurrent" />
      <!-- 全部资料 -->
      <template v-if="!error && listMode === 'all'">
        <div v-if="materialList.length" class="material-list-pro">
          <MaterialCard
            v-for="item in materialList"
            :key="item.material_id"
            :material="item"
            :keyword="searchKeyword"
            :course-name="courseName(item.course_id)"
            @favorite="handleFavorite"
            @download="downloadMaterial"
          />
        </div>
        <EmptyState v-else-if="!loading" description="暂无资料" />
        <el-pagination
          v-if="total > pageSize"
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="handlePageChange"
        />
      </template>

      <!-- 我的上传 -->
      <template v-else-if="!error">
        <div class="upload-summary-list" v-loading="loading">
          <article v-for="row in myMaterials" :key="row.material_id" class="upload-summary card">
            <div class="upload-summary-heading"><h3>{{ row.material_name }}</h3><el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : row.status === 3 ? 'info' : 'warning'">{{ ['待审核', '已发布', '已驳回', '已下架'][row.status] || '未知状态' }}</el-tag></div>
            <p>{{ MATERIAL_TYPE_LABEL[row.material_type] || '其他' }} · V{{ row.revision || 1 }} · 下载 {{ row.download_count || 0 }} 次</p>
            <small>上传于 {{ formatTime(row.create_time) || '时间未记录' }}</small>
            <p v-if="row.status === 2 && row.reject_reason" class="reject-reason">驳回原因：{{ row.reject_reason }}</p>
            <div class="upload-summary-actions"><el-button size="small" @click="$router.push(`/material/${row.material_id}`)">查看详情</el-button><el-button v-if="row.status === 2" size="small" type="warning" @click="handleResubmit(row)">修改重提</el-button></div>
          </article>
        </div>
        <el-table class="upload-table" :data="myMaterials" border stripe v-loading="loading">
          <el-table-column prop="material_name" label="资料名称" min-width="200" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ MATERIAL_TYPE_LABEL[row.material_type] || '其他' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : row.status === 3 ? 'info' : 'warning'" size="small">
                {{ row.status === 1 ? '已发布' : row.status === 2 ? '已驳回' : row.status === 3 ? '已下架' : '待审核' }}
              </el-tag>
              <div v-if="row.status === 2 && row.reject_reason" class="reject-reason">{{ row.reject_reason }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="download_count" label="下载" width="70" />
          <el-table-column prop="create_time" label="上传时间" width="160">
            <template #default="{ row }">{{ formatTime(row.create_time) }}</template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="190">
            <template #default="{ row }">
              <el-button size="small" @click="$router.push(`/material/${row.material_id}`)">查看</el-button>
              <el-button v-if="row.status === 2" size="small" type="warning" @click="handleResubmit(row)">修改重提</el-button>
            </template>
          </el-table-column>
        </el-table>
        <EmptyState v-if="!loading && myMaterials.length === 0" description="暂无上传记录" />
      </template>

      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon>
        加载中...
      </div>

      <MaterialEditor v-model="showUploadDialog" :material="editingMaterial" :courses="courseOptions" @saved="afterSaved" />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { Search, Loading, UploadFilled, Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Toolbar from '@/components/Toolbar.vue'
import MaterialCard from '@/components/MaterialCard.vue'
import MaterialEditor from '@/components/MaterialEditor.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import { MATERIAL_TYPE_LABEL } from '@/utils/constants'
import { useUserStore } from '@/stores/user'
import { useRequestGate, useListMemory } from '@/composables/useListQuery'
import * as materialApi from '@/api/material'
import * as favoriteApi from '@/api/favorite'

const userStore = useUserStore(), route = useRoute()
const materialList = ref([]), myMaterials = ref([]), courseOptions = ref([])
const loading = ref(false), error = ref(''), currentPage = ref(1), total = ref(0)
const pageSize = 12
const listMode = ref(route.query.tab === 'my' ? 'my' : 'all')
const searchKeyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const priceFilter = ref('all'), showUploadDialog = ref(false), editingMaterial = ref(null)
const filters = reactive({ courseId: Number(route.query.courseId) || null, materialType: null, sortBy: 'download_count' })
const gate = useRequestGate(() => { materialList.value = []; myMaterials.value = []; loading.value = false; showUploadDialog.value = false })
const restoreScroll = useListMemory(
  () => ({ keyword: searchKeyword.value, filters: { ...filters }, page: currentPage.value, price: priceFilter.value, mode: listMode.value }),
  state => { searchKeyword.value = state.keyword; Object.assign(filters, state.filters); currentPage.value = state.page; priceFilter.value = state.price; listMode.value = state.mode },
  (to, restored) => {
    clearTimeout(searchTimer)
    if (!restored) {
      searchKeyword.value = typeof to.query.keyword === 'string' ? to.query.keyword : ''
      Object.assign(filters, { courseId: Number(to.query.courseId) || null, materialType: null, sortBy: 'download_count' })
      listMode.value = to.query.tab === 'my' ? 'my' : 'all'; priceFilter.value = 'all'; currentPage.value = 1
    }
    loadCurrent()
  }
)
let searchTimer
const busyItems = new Set()
function courseName(id) { return courseOptions.value.find(item => item.course_id === Number(id))?.course_name || '' }
async function fetchCourses() { try { const res = await materialApi.getCourseOptions(); courseOptions.value = res.data?.list || [] } catch { /* association remains optional */ } }
async function loadCurrent() {
  const ticket = gate.begin(), mode = listMode.value
  loading.value = true; error.value = ''
  try {
    const res = mode === 'my' ? await materialApi.getMyUploads() : await materialApi.getMaterialList({ page: currentPage.value, pageSize, ...filters, keyword: searchKeyword.value || undefined, priceFilter: priceFilter.value })
    if (!gate.valid(ticket)) return
    if (mode === 'my') myMaterials.value = res.data?.list || res.data || []
    else { materialList.value = res.data?.list || []; total.value = Number(res.data?.total || 0) }
    await restoreScroll()
  } catch (err) { if (gate.valid(ticket) && !err.sessionChanged) { error.value = '资料加载失败'; materialList.value = []; myMaterials.value = [] } }
  finally { if (gate.valid(ticket)) loading.value = false }
}
function applyFilters() { clearTimeout(searchTimer); currentPage.value = 1; loadCurrent() }
function onSearchInput() { clearTimeout(searchTimer); gate.invalidate(); searchTimer = setTimeout(applyFilters, 260) }
function handlePageChange() { loadCurrent() }
async function handleFavorite(material) {
  if (busyItems.has(material.material_id)) return
  busyItems.add(material.material_id)
  try {
    if (material.is_favorited) await favoriteApi.removeFavorite({ type: 'material', target_id: material.material_id })
    else await favoriteApi.addFavorite({ type: 'material', target_id: material.material_id })
    material.is_favorited = !material.is_favorited
    ElMessage.success(material.is_favorited ? '收藏成功' : '已取消收藏')
  } catch { /* request displays failure */ }
  finally { busyItems.delete(material.material_id) }
}
async function downloadMaterial(material) {
  try {
    if (Number(material.download_score) > 0) await ElMessageBox.confirm(`下载「${material.material_name}」最多消耗 ${material.download_score} 积分，已下载过的资料不会重复扣分。`, '下载确认')
    const response = await materialApi.downloadMaterialFile(material.material_id)
    materialApi.saveBlobResponse(response, material.material_name || 'download')
    const balance = response.headers?.['x-score-balance']
    if (balance !== undefined) userStore.updateScoreBalance(Number(balance))
    ElMessage.success('开始下载')
  } catch { /* cancelled or request displays failure */ }
}
function openUpload() { editingMaterial.value = null; showUploadDialog.value = true }
async function handleResubmit(row) {
  try { const res = await materialApi.getMaterialDetail(row.material_id); editingMaterial.value = res.data?.material || row; showUploadDialog.value = true }
  catch { /* user can retry from row */ }
}
function afterSaved() { listMode.value = 'my'; applyFilters() }
function formatTime(time) { return time ? new Date(time).toLocaleDateString('zh-CN') : '' }
onMounted(() => { fetchCourses(); loadCurrent() })
onBeforeUnmount(() => clearTimeout(searchTimer))
</script>

<style scoped>
.score-rule {
  margin-bottom: var(--spacing-lg);
  padding: 10px 16px;
  border-radius: 12px;
  color: var(--color-primary-dark);
  background: var(--color-primary-light);
  font-weight: 500;
}

.material-list-pro {
  display: grid;
  gap: var(--spacing-md);
}

.upload-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-md);
}

.upload-tip {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
}

.load-more {
  text-align: center;
  padding: var(--spacing-lg);
  color: var(--color-text-secondary);
}
.reject-reason { margin-top: 4px; color: var(--color-danger); font-size: var(--font-size-xs); }
.upload-summary-list { display: none; }
.upload-summary { min-width: 0; padding: 16px; }
.upload-summary-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.upload-summary h3 { margin: 0; overflow-wrap: anywhere; }
.upload-summary .el-tag { flex-shrink: 0; }
.upload-summary p, .upload-summary small { overflow-wrap: anywhere; color: var(--color-text-secondary); }
.upload-summary .reject-reason { color: var(--color-danger); }
.upload-summary-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
@media (max-width: 768px) { .upload-table { display: none; } .upload-summary-list { display: grid; gap: 12px; } }

@media (max-width: 767px) {
  .upload-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
