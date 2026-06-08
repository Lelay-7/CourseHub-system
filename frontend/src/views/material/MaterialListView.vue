<template>
  <AppLayout>
    <PageContainer compact>
      <PageHeader
        title="学习资料"
        subtitle="查找复习提纲、实验模板、课件和往年题。"
      >
        <template #extra>
          <el-button type="primary" :icon="UploadFilled" @click="showUploadDialog = true">
            上传资料
          </el-button>
        </template>
      </PageHeader>

      <Toolbar>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件名、上传者、关键词"
          :prefix-icon="Search"
          clearable
          @input="onSearchInput"
        />
        <el-select v-model="filters.courseId" placeholder="关联课程" clearable filterable @change="applyFilters">
          <el-option v-for="course in courseOptions" :key="course.category_id" :label="course.category_name" :value="course.category_id" />
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
          <el-radio-button value="rating">评分</el-radio-button>
        </el-radio-group>
      </Toolbar>

      <div class="score-rule">
        上传优质资料可获得积分，积分可用于下载资料和商城兑换。
      </div>

      <div v-if="visibleMaterials.length" class="material-list-pro">
        <MaterialCard
          v-for="item in visibleMaterials"
          :key="item.material_id"
          :material="item"
          :keyword="searchKeyword"
          :course-name="courseName(item.course_id)"
          @favorite="handleFavorite"
          @download="downloadMaterial"
        />
      </div>
      <EmptyState v-else-if="!loading" description="暂无资料" />

      <div v-if="loading" class="load-more">
        <el-icon class="is-loading"><Loading /></el-icon>
        加载中...
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
                <el-option v-for="course in courseOptions" :key="course.category_id" :label="course.category_name" :value="course.category_id" />
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
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Loading, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Toolbar from '@/components/Toolbar.vue'
import MaterialCard from '@/components/MaterialCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { MATERIAL_TYPE, MATERIAL_TYPE_LABEL } from '@/utils/constants'
import { useUserStore } from '@/stores/user'
import * as materialApi from '@/api/material'
import * as favoriteApi from '@/api/favorite'

const userStore = useUserStore()
const materialList = ref([])
const courseOptions = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const priceFilter = ref('all')
const showUploadDialog = ref(false)
const uploadSubmitting = ref(false)
const selectedUploadFile = ref(null)

const filters = reactive({
  courseId: null,
  materialType: null,
  sortBy: 'download_count'
})

const uploadForm = reactive({
  material_name: '',
  course_id: null,
  material_type: MATERIAL_TYPE.PDF,
  download_score: 0,
  keyword: ''
})

const visibleMaterials = computed(() => {
  if (priceFilter.value === 'free') return materialList.value.filter(item => !item.download_score)
  if (priceFilter.value === 'score') return materialList.value.filter(item => item.download_score > 0)
  return materialList.value
})

function courseName(id) {
  return courseOptions.value.find(item => item.category_id === Number(id))?.category_name || '关联课程'
}

async function fetchCategories() {
  const res = await materialApi.getCategories()
  if (res.code === 200) courseOptions.value = res.data || []
}

async function fetchList() {
  loading.value = true
  try {
    const res = await materialApi.getMaterialList({
      page: 1,
      pageSize: 30,
      ...filters,
      keyword: searchKeyword.value || undefined
    })
    if (res.code === 200) {
      const data = res.data || {}
      materialList.value = data.list || data.records || []
    }
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  fetchList()
}

let searchTimer = null
function onSearchInput(value) {
  if (value && value.length < 2) return
  clearTimeout(searchTimer)
  searchTimer = setTimeout(fetchList, 260)
}

async function handleFavorite(material) {
  if (material.is_favorited) {
    await favoriteApi.removeFavorite({ type: 'material', target_id: material.material_id })
    material.is_favorited = false
    ElMessage.success('已取消收藏')
  } else {
    await favoriteApi.addFavorite({ type: 'material', target_id: material.material_id })
    material.is_favorited = true
    ElMessage.success('收藏成功')
  }
}

async function downloadMaterial(material) {
  const res = await materialApi.getDownloadUrl(material.material_id)
  if (res.code === 200) {
    if (res.data?.score_balance !== undefined) userStore.updateScoreBalance(res.data.score_balance)
    ElMessage.success(`开始下载：${material.material_name}`)
  }
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
      if (res.data?.material) materialList.value.unshift(res.data.material)
      if (res.data?.score_balance !== undefined) userStore.updateScoreBalance(res.data.score_balance)
      ElMessage.success('资料已上传，积分 +2')
      showUploadDialog.value = false
      resetUploadForm()
    }
  } finally {
    uploadSubmitting.value = false
  }
}

onMounted(() => {
  fetchCategories()
  fetchList()
})
</script>

<style scoped>
.score-rule {
  margin-bottom: var(--spacing-lg);
  padding: 16px 20px;
  border-radius: 22px;
  color: var(--color-primary-dark);
  background: #ffe9df;
  font-weight: 900;
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

@media (max-width: 767px) {
  .upload-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
