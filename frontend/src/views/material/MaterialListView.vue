<template>
  <AppLayout>
    <div class="page-content material-list-page">
      <PageHeader title="学习资料">
        <template #extra>
          <el-button type="primary" :icon="UploadFilled" @click="showUploadDialog = true">
            上传资料
          </el-button>
        </template>
      </PageHeader>

      <!-- 搜索 + 筛选 -->
      <div class="filter-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件名、上传者、关键词"
          :prefix-icon="Search"
          clearable
          class="search-input"
          @input="onSearchInput"
        />
        <el-select
          v-model="filters.courseId"
          placeholder="关联课程"
          clearable
          filterable
          style="width: 150px"
          @change="applyFilters"
        >
          <el-option
            v-for="course in courseOptions"
            :key="course.category_id"
            :label="course.category_name"
            :value="course.category_id"
          />
        </el-select>
        <el-select
          v-model="filters.materialType"
          placeholder="文件类型"
          clearable
          style="width: 130px"
          @change="applyFilters"
        >
          <el-option v-for="(label, val) in MATERIAL_TYPE_LABEL" :key="val" :label="label" :value="Number(val)" />
        </el-select>
        <el-select
          v-model="filters.timeRange"
          placeholder="上传时间"
          clearable
          style="width: 120px"
          @change="applyFilters"
        >
          <el-option v-for="t in MATERIAL_TIME_FILTER" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </div>

      <!-- 排序 -->
      <div class="sort-bar flex-between">
        <span class="total-count">共 {{ totalCount }} 份资料</span>
        <el-radio-group v-model="filters.sortBy" size="small" @change="applyFilters">
          <el-radio-button value="download_count">下载量</el-radio-button>
          <el-radio-button value="create_time">最新</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 资料列表 -->
      <div v-if="materialList.length > 0" class="material-list">
        <MaterialCard
          v-for="item in materialList"
          :key="item.material_id"
          :material="item"
          :keyword="searchKeyword"
          @favorite="handleFavorite"
        />
      </div>
      <EmptyState v-else-if="!loading" description="暂无资料" />

      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon> 加载中...
      </div>
      <div v-else-if="noMore && materialList.length > 0" class="load-more no-more">
        没有更多了
      </div>

      <el-dialog v-model="showUploadDialog" title="上传学习资料" width="560px">
        <el-form label-position="top">
          <el-form-item label="选择文件">
            <el-upload
              :auto-upload="false"
              :limit="1"
              :on-change="onUploadFileChange"
              :on-remove="onUploadFileRemove"
            >
              <el-button :icon="UploadFilled">选择文件</el-button>
              <template #tip>
                <div class="upload-tip">单文件不超过 50MB，支持 PDF、Word、Excel、PPT、图片和压缩包。</div>
              </template>
            </el-upload>
          </el-form-item>
          <el-form-item label="资料名称">
            <el-input v-model="uploadForm.material_name" maxlength="80" show-word-limit />
          </el-form-item>
          <div class="upload-grid">
            <el-form-item label="关联课程">
              <el-select v-model="uploadForm.course_id" placeholder="请选择课程">
                <el-option
                  v-for="course in courseOptions"
                  :key="course.category_id"
                  :label="course.category_name"
                  :value="course.category_id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="资料类型">
              <el-select v-model="uploadForm.material_type" placeholder="请选择类型">
                <el-option v-for="(label, val) in MATERIAL_TYPE_LABEL" :key="val" :label="label" :value="Number(val)" />
              </el-select>
            </el-form-item>
          </div>
          <div class="upload-grid">
            <el-form-item label="下载积分">
              <el-input-number v-model="uploadForm.download_score" :min="0" :max="50" />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="uploadForm.keyword" placeholder="用于搜索匹配" />
            </el-form-item>
          </div>
        </el-form>
        <template #footer>
          <el-button @click="showUploadDialog = false">取消</el-button>
          <el-button type="primary" :loading="uploadSubmitting" @click="submitUpload">
            提交上传
          </el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted, onActivated, nextTick } from 'vue'
import { Search, Loading, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import MaterialCard from '@/components/MaterialCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useMaterialStore } from '@/stores/material'
import { MATERIAL_TYPE, MATERIAL_TYPE_LABEL, MATERIAL_TIME_FILTER } from '@/utils/constants'
import * as materialApi from '@/api/material'
import * as favoriteApi from '@/api/favorite'
import { useUserStore } from '@/stores/user'

const materialStore = useMaterialStore()
const userStore = useUserStore()

const materialList = ref([])
const loading = ref(false)
const page = ref(1)
const noMore = ref(false)
const totalCount = ref(0)
const searchKeyword = ref('')
const courseOptions = ref([])
const showUploadDialog = ref(false)
const uploadSubmitting = ref(false)
const selectedUploadFile = ref(null)
const filters = reactive({ ...materialStore.filters })
const uploadForm = reactive({
  material_name: '',
  course_id: null,
  material_type: MATERIAL_TYPE.PDF,
  download_score: 0,
  keyword: ''
})

async function fetchCategories() {
  try {
    const res = await materialApi.getCategories()
    if (res.code === 200) courseOptions.value = res.data || []
  } catch { /* handled */ }
}

async function fetchList(reset = false) {
  if (loading.value) return
  if (reset) { page.value = 1; materialList.value = []; noMore.value = false }
  loading.value = true
  try {
    const res = await materialApi.getMaterialList({
      page: page.value,
      pageSize: 20,
      ...filters,
      keyword: searchKeyword.value || undefined
    })
    if (res.code === 200) {
      const data = res.data || {}
      const list = data.list || data.records || []
      if (reset) materialList.value = list
      else materialList.value.push(...list)
      totalCount.value = data.total || 0
      noMore.value = materialList.value.length >= totalCount.value
      materialStore.setMaterialList(materialList.value, totalCount.value)
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

function applyFilters() {
  Object.assign(materialStore.filters, filters)
  fetchList(true)
}

let searchTimer = null
function onSearchInput(val) {
  if (val && val.length < 2) return
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { filters.keyword = val; applyFilters() }, 300)
}

async function handleFavorite(material) {
  try {
    if (material.is_favorited) {
      await favoriteApi.removeFavorite({ type: 'material', target_id: material.material_id })
      material.is_favorited = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteApi.addFavorite({ type: 'material', target_id: material.material_id })
      material.is_favorited = true
      ElMessage.success('收藏成功')
    }
  } catch { /* handled */ }
}

function inferMaterialType(fileName = '') {
  const ext = fileName.split('.').pop()?.toLowerCase()
  if (ext === 'pdf') return MATERIAL_TYPE.PDF
  if (['doc', 'docx'].includes(ext)) return MATERIAL_TYPE.WORD
  if (['xls', 'xlsx', 'csv'].includes(ext)) return MATERIAL_TYPE.EXCEL
  if (['ppt', 'pptx'].includes(ext)) return MATERIAL_TYPE.PPT
  if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext)) return MATERIAL_TYPE.IMAGE
  return MATERIAL_TYPE.ARCHIVE
}

function onUploadFileChange(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 50MB')
    selectedUploadFile.value = null
    return
  }
  selectedUploadFile.value = file
  if (!uploadForm.material_name) uploadForm.material_name = file.name.replace(/\.[^.]+$/, '')
  uploadForm.material_type = inferMaterialType(file.name)
}

function onUploadFileRemove() {
  selectedUploadFile.value = null
}

function resetUploadForm() {
  selectedUploadFile.value = null
  Object.assign(uploadForm, {
    material_name: '',
    course_id: null,
    material_type: MATERIAL_TYPE.PDF,
    download_score: 0,
    keyword: ''
  })
}

async function submitUpload() {
  if (!selectedUploadFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  if (!uploadForm.material_name.trim()) {
    ElMessage.warning('请填写资料名称')
    return
  }
  if (!uploadForm.course_id) {
    ElMessage.warning('请选择关联课程')
    return
  }
  uploadSubmitting.value = true
  try {
    const res = await materialApi.uploadMaterial({
      ...uploadForm,
      material_name: uploadForm.material_name.trim(),
      file_name: selectedUploadFile.value.name,
      file_url: URL.createObjectURL(selectedUploadFile.value)
    })
    if (res.code === 200) {
      if (res.data?.material) {
        materialList.value.unshift(res.data.material)
        totalCount.value += 1
      }
      if (res.data?.score_balance !== undefined) userStore.updateScoreBalance(res.data.score_balance)
      ElMessage.success('资料已上传，积分 +2')
      showUploadDialog.value = false
      resetUploadForm()
    }
  } catch { /* handled */ }
  finally { uploadSubmitting.value = false }
}

function onScroll() {
  const el = document.querySelector('.app-content')
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  if (scrollHeight - scrollTop - clientHeight < 200) {
    if (!noMore.value && !loading.value) { page.value++; fetchList(false) }
  }
  materialStore.saveScrollTop(scrollTop)
}

onMounted(() => {
  fetchCategories()
  fetchList(true)
  nextTick(() => {
    const el = document.querySelector('.app-content')
    if (el && materialStore.scrollTop) el.scrollTop = materialStore.scrollTop
    el?.addEventListener('scroll', onScroll)
  })
})

onActivated(() => {
  nextTick(() => {
    const el = document.querySelector('.app-content')
    if (el && materialStore.scrollTop) el.scrollTop = materialStore.scrollTop
  })
})
</script>

<style scoped>
.material-list-page {
  --content-max-width: 1120px;
}

.filter-bar {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}
.search-input { flex: 1; min-width: 200px; }
.sort-bar { margin-bottom: var(--spacing-md); }
.total-count { font-size: var(--font-size-sm); color: var(--color-text-secondary); }
.material-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
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
.load-more { text-align: center; padding: var(--spacing-md); color: var(--color-text-secondary); }
.load-more.no-more { color: var(--color-text-placeholder); }

@media (max-width: 767px) {
  .upload-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
