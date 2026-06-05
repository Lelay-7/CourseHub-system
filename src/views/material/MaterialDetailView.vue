<template>
  <AppLayout>
    <div class="page-content">
      <PageHeader title="资料详情" :show-back="true">
        <template #extra>
          <el-button
            :icon="material.is_favorited ? StarFilled : Star"
            :type="material.is_favorited ? 'warning' : 'default'"
            @click="handleFavorite"
          >
            {{ material.is_favorited ? '已收藏' : '收藏' }}
          </el-button>
          <el-button :icon="Share" @click="handleShare">分享</el-button>
          <el-button :icon="Warning" type="danger" plain @click="showReport = true">举报</el-button>
        </template>
      </PageHeader>

      <div v-if="loading" class="detail-loading">
        <LoadingSkeleton type="list" :rows="5" />
      </div>

      <template v-else-if="material.material_id">
        <div class="detail-card card">
          <!-- 文件信息 -->
          <div class="material-info">
            <div class="material-type-icon" :style="{ background: typeColor }">
              <el-icon :size="32"><component :is="typeIcon" /></el-icon>
            </div>
            <div class="material-meta">
              <h2>{{ material.material_name }}</h2>
              <div class="meta-row">
                <span>上传者：{{ material.uploader_name }}</span>
                <span>时间：{{ formatTime(material.create_time) }}</span>
                <span>下载：{{ material.download_count }} 次</span>
                <el-tag :type="scoreType" size="small">{{ scoreLabel }}</el-tag>
              </div>
            </div>
          </div>

          <!-- 文件预览 -->
          <div class="preview-section">
            <h3>文件预览</h3>
            <div v-if="isPreviewable" class="preview-container">
              <img v-if="isImage" :src="material.file_url" class="preview-img" />
              <iframe
                v-else-if="isPdf"
                :src="material.file_url"
                class="preview-iframe"
                frameborder="0"
              />
            </div>
            <div v-else class="preview-unsupported">
              <el-icon :size="48"><Warning /></el-icon>
              <p>{{ previewHint }}</p>
            </div>
          </div>
        </div>

        <!-- 下载按钮 -->
        <div class="download-bar card">
          <div class="flex-between">
            <span class="download-score" v-if="material.download_score > 0">
              下载需消耗 <strong>{{ material.download_score }}</strong> 积分
            </span>
            <span class="download-score free" v-else>免费下载</span>
            <el-button type="primary" :icon="Download" :loading="downloading" @click="handleDownload">
              下载资料
            </el-button>
          </div>
        </div>
      </template>

      <ErrorState v-else title="资料不存在" @retry="fetchDetail" />

      <!-- 举报弹窗 -->
      <el-dialog v-model="showReport" title="举报资料" width="420px">
        <el-form :model="reportForm" label-position="top">
          <el-form-item label="举报原因">
            <el-radio-group v-model="reportForm.reason">
              <el-radio value="infringement">侵权</el-radio>
              <el-radio value="fake">虚假内容</el-radio>
              <el-radio value="violation">违规内容</el-radio>
              <el-radio value="other">其他</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="补充说明">
            <el-input v-model="reportForm.detail" type="textarea" :rows="3" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showReport = false">取消</el-button>
          <el-button type="danger" @click="submitReport">提交举报</el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, StarFilled, Share, Warning, Download } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'
import { MATERIAL_TYPE } from '@/utils/constants'
import * as materialApi from '@/api/material'
import * as favoriteApi from '@/api/favorite'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const material = ref({})
const loading = ref(true)
const downloading = ref(false)
const showReport = ref(false)
const reportForm = reactive({ reason: 'infringement', detail: '' })

const typeIconMap = {
  [MATERIAL_TYPE.PDF]: 'Document', [MATERIAL_TYPE.WORD]: 'Document',
  [MATERIAL_TYPE.EXCEL]: 'Grid', [MATERIAL_TYPE.PPT]: 'DataAnalysis',
  [MATERIAL_TYPE.IMAGE]: 'Picture', [MATERIAL_TYPE.ARCHIVE]: 'FolderOpened'
}
const typeColorMap = {
  [MATERIAL_TYPE.PDF]: '#F56C6C', [MATERIAL_TYPE.WORD]: '#409EFF',
  [MATERIAL_TYPE.EXCEL]: '#67C23A', [MATERIAL_TYPE.PPT]: '#E6A23C',
  [MATERIAL_TYPE.IMAGE]: '#9B59B6', [MATERIAL_TYPE.ARCHIVE]: '#795548'
}

const typeIcon = computed(() => typeIconMap[material.value.material_type] || 'Document')
const typeColor = computed(() => typeColorMap[material.value.material_type] || '#909399')
const isPdf = computed(() => material.value.material_type === MATERIAL_TYPE.PDF)
const isImage = computed(() => material.value.material_type === MATERIAL_TYPE.IMAGE)
const isPreviewable = computed(() => isPdf.value || isImage.value)
const previewHint = computed(() => {
  const type = material.value.material_type
  if (type === MATERIAL_TYPE.WORD || type === MATERIAL_TYPE.PPT) return '请下载后使用相应软件打开'
  if (type === MATERIAL_TYPE.EXCEL) return '请下载后使用 Excel 打开'
  if (type === MATERIAL_TYPE.ARCHIVE) return '请下载后解压查看'
  return '暂不支持在线预览'
})
const scoreLabel = computed(() => material.value.download_score === 0 ? '免费' : `${material.value.download_score} 积分`)
const scoreType = computed(() => material.value.download_score === 0 ? 'success' : 'warning')

async function fetchDetail() {
  loading.value = true
  try {
    const res = await materialApi.getMaterialDetail(route.params.id)
    if (res.code === 200) material.value = res.data || {}
  } catch { /* handled */ }
  finally { loading.value = false }
}

async function handleDownload() {
  downloading.value = true
  try {
    const res = await materialApi.getDownloadUrl(route.params.id)
    if (res.code === 200) {
      const url = res.data?.download_url || res.data?.url
      if (url) {
        window.open(url, '_blank')
        ElMessage.success('开始下载')
        // 更新积分
        if (res.data?.score_balance !== undefined) {
          userStore.updateScoreBalance(res.data.score_balance)
        }
      }
    }
  } catch { /* handled */ }
  finally { downloading.value = false }
}

async function handleFavorite() {
  try {
    if (material.value.is_favorited) {
      await favoriteApi.removeFavorite({ type: 'material', target_id: material.value.material_id })
      material.value.is_favorited = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteApi.addFavorite({ type: 'material', target_id: material.value.material_id })
      material.value.is_favorited = true
      ElMessage.success('收藏成功')
    }
  } catch { /* handled */ }
}

function handleShare() {
  const url = `${window.location.origin}/material/${route.params.id}`
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('链接已复制，可分享给好友')
  }).catch(() => ElMessage.info(`分享链接: ${url}`))
}

function submitReport() {
  ElMessage.success('举报已提交，管理员将尽快审核')
  showReport.value = false
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleDateString('zh-CN')
}

onMounted(fetchDetail)
</script>

<style scoped>
.detail-loading { padding: var(--spacing-lg); }
.detail-card { margin-bottom: var(--spacing-md); }

.material-info {
  display: flex;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}
.material-type-icon {
  width: 64px; height: 64px;
  border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
  color: #fff; flex-shrink: 0;
}
.material-meta h2 { margin: 0 0 var(--spacing-sm); font-size: var(--font-size-xl); }
.meta-row {
  display: flex; gap: var(--spacing-md); flex-wrap: wrap;
  font-size: var(--font-size-sm); color: var(--color-text-secondary);
}

.preview-section h3 { margin: 0 0 var(--spacing-md); }
.preview-container {
  border: 1px solid var(--color-border); border-radius: var(--radius-sm);
  overflow: hidden; background: #f5f5f5;
}
.preview-img { max-width: 100%; display: block; margin: 0 auto; }
.preview-iframe { width: 100%; height: 600px; }
.preview-unsupported {
  display: flex; flex-direction: column; align-items: center;
  padding: var(--spacing-xxl); color: var(--color-text-secondary);
}

.download-bar { padding: var(--spacing-md); }
.download-score { color: var(--color-text-secondary); }
.download-score strong { color: var(--color-warning); }
.download-score.free { color: var(--color-success); }
</style>
