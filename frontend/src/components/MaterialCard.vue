<template>
  <div class="material-card card card-hover" @click="goDetail">
    <div class="material-left">
      <!-- 类型图标 -->
      <div class="material-type-icon" :style="{ background: typeColor }">
        <el-icon :size="24"><component :is="typeIcon" /></el-icon>
      </div>
    </div>

    <div class="material-right">
      <h4 class="material-name text-ellipsis" v-html="highlightedName" />
      <div class="material-meta">
        <span>{{ material.uploader_name || '未知' }}</span>
        <span class="meta-divider">|</span>
        <span>{{ formatTime(material.create_time) }}</span>
        <span class="meta-divider">|</span>
        <span>
          <el-icon><Download /></el-icon>
          {{ formatNumber(material.download_count) }}
        </span>
      </div>
      <div class="material-footer">
        <el-tag :type="scoreType" size="small">
          {{ scoreLabel }}
        </el-tag>
      </div>
    </div>

    <!-- 收藏按钮 -->
    <el-button
      class="favorite-btn"
      :icon="material.is_favorited ? StarFilled : Star"
      :type="material.is_favorited ? 'warning' : 'default'"
      circle
      size="small"
      @click.stop="toggleFavorite"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Star, StarFilled, Download } from '@element-plus/icons-vue'
import {
  MATERIAL_TYPE, MATERIAL_TYPE_ICON
} from '@/utils/constants'

const props = defineProps({
  material: { type: Object, required: true },
  keyword: { type: String, default: '' }
})

const emit = defineEmits(['favorite'])
const router = useRouter()

// 根据资料类型映射 Element Plus 图标组件（运行时动态加载）
const iconMap = {
  [MATERIAL_TYPE.PDF]: 'Document',
  [MATERIAL_TYPE.WORD]: 'Document',
  [MATERIAL_TYPE.EXCEL]: 'Grid',
  [MATERIAL_TYPE.PPT]: 'DataAnalysis',
  [MATERIAL_TYPE.IMAGE]: 'Picture',
  [MATERIAL_TYPE.ARCHIVE]: 'FolderOpened'
}
const colorMap = {
  [MATERIAL_TYPE.PDF]: '#d95d5d',
  [MATERIAL_TYPE.WORD]: '#16697a',
  [MATERIAL_TYPE.EXCEL]: '#2a9d8f',
  [MATERIAL_TYPE.PPT]: '#e9a23b',
  [MATERIAL_TYPE.IMAGE]: '#7c6a9e',
  [MATERIAL_TYPE.ARCHIVE]: '#8a6f4d'
}

const typeIcon = computed(() => iconMap[props.material.material_type] || 'Document')
const typeColor = computed(() => colorMap[props.material.material_type] || '#909399')

const scoreLabel = computed(() =>
  props.material.download_score === 0 ? '免费' : `${props.material.download_score} 积分`
)
const scoreType = computed(() =>
  props.material.download_score === 0 ? 'success' : 'warning'
)
const highlightedName = computed(() => highlightText(props.material.material_name || '', props.keyword))

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[char]))
}

function highlightText(text, keyword) {
  if (!keyword || keyword.length < 2) return escapeHtml(text)
  const escaped = escapeHtml(text)
  const pattern = escapeHtml(keyword).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return escaped.replace(new RegExp(pattern, 'gi'), match => `<mark>${match}</mark>`)
}

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diff = now - d
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return d.toLocaleDateString('zh-CN')
}

function formatNumber(num) {
  if (!num) return '0'
  if (num >= 10000) return (num / 10000).toFixed(1) + '万'
  return String(num)
}

function goDetail() {
  router.push(`/material/${props.material.material_id}`)
}

function toggleFavorite() {
  emit('favorite', props.material)
}
</script>

<style scoped>
.material-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  min-height: 86px;
  cursor: pointer;
  position: relative;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.material-left {
  flex-shrink: 0;
}

.material-type-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.material-right {
  flex: 1;
  min-width: 0;
}

.material-name {
  margin: 0 0 6px;
  font-size: var(--font-size-base);
  font-weight: 700;
  color: var(--color-text-primary);
}

.material-name :deep(mark) {
  color: var(--color-primary-dark);
  background: rgba(239, 131, 84, 0.28);
  border-radius: 3px;
  padding: 0 2px;
}

.material-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.meta-divider {
  color: var(--color-border);
}

.favorite-btn {
  flex-shrink: 0;
  margin-left: auto;
  background: rgba(255, 253, 248, 0.9);
}

@media (max-width: 767px) {
  .material-card {
    align-items: flex-start;
    gap: var(--spacing-sm);
  }
  .material-type-icon {
    width: 44px;
    height: 44px;
  }
  .material-meta {
    flex-wrap: wrap;
    row-gap: 2px;
  }
}
</style>
