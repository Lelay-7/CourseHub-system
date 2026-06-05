<template>
  <div class="material-card card card-hover" @click="goDetail">
    <div class="material-left">
      <!-- 类型图标 -->
      <div class="material-type-icon" :style="{ background: typeColor }">
        <el-icon :size="24"><component :is="typeIcon" /></el-icon>
      </div>
    </div>

    <div class="material-right">
      <h4 class="material-name text-ellipsis">{{ material.material_name }}</h4>
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
  material: { type: Object, required: true }
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
  [MATERIAL_TYPE.PDF]: '#F56C6C',
  [MATERIAL_TYPE.WORD]: '#409EFF',
  [MATERIAL_TYPE.EXCEL]: '#67C23A',
  [MATERIAL_TYPE.PPT]: '#E6A23C',
  [MATERIAL_TYPE.IMAGE]: '#9B59B6',
  [MATERIAL_TYPE.ARCHIVE]: '#795548'
}

const typeIcon = computed(() => iconMap[props.material.material_type] || 'Document')
const typeColor = computed(() => colorMap[props.material.material_type] || '#909399')

const scoreLabel = computed(() =>
  props.material.download_score === 0 ? '免费' : `${props.material.download_score} 积分`
)
const scoreType = computed(() =>
  props.material.download_score === 0 ? 'success' : 'warning'
)

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
  cursor: pointer;
  position: relative;
}

.material-left {
  flex-shrink: 0;
}

.material-type-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-sm);
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
  font-weight: 500;
  color: var(--color-text-primary);
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
}
</style>
