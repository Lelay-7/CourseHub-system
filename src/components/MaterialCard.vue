<template>
  <article class="material-card-pro" @click="$router.push(`/material/${material.material_id}`)">
    <div class="material-card-pro__icon" :class="typeClass">
      <el-icon :size="26"><component :is="typeIcon" /></el-icon>
    </div>

    <div class="material-card-pro__body">
      <div class="material-card-pro__title">
        <h3 v-html="highlightedName" />
        <StatusTag :tone="material.download_score ? 'warning' : 'success'">
          {{ material.download_score ? `${material.download_score} 积分` : '免费' }}
        </StatusTag>
      </div>

      <div class="material-card-pro__meta">
        <span>{{ material.uploader_name || '未知上传者' }}</span>
        <span>{{ formatTime(material.create_time) }}</span>
        <span><el-icon><Download /></el-icon>{{ formatNumber(material.download_count) }} 下载</span>
        <span>{{ courseName }}</span>
        <span>{{ typeLabel }}</span>
      </div>
    </div>

    <div class="material-card-pro__actions">
      <el-button plain @click.stop="$emit('favorite', material)">
        {{ material.is_favorited ? '已收藏' : '收藏' }}
      </el-button>
      <el-button type="primary" @click.stop="$emit('download', material)">下载</el-button>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { Download } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { MATERIAL_TYPE, MATERIAL_TYPE_LABEL } from '@/utils/constants'

const props = defineProps({
  material: { type: Object, required: true },
  keyword: { type: String, default: '' },
  courseName: { type: String, default: '关联课程待确认' }
})

defineEmits(['favorite', 'download'])

const iconMap = {
  [MATERIAL_TYPE.PDF]: 'Document',
  [MATERIAL_TYPE.WORD]: 'Document',
  [MATERIAL_TYPE.EXCEL]: 'Grid',
  [MATERIAL_TYPE.PPT]: 'DataAnalysis',
  [MATERIAL_TYPE.IMAGE]: 'Picture',
  [MATERIAL_TYPE.ARCHIVE]: 'FolderOpened'
}

const typeIcon = computed(() => iconMap[props.material.material_type] || 'Document')
const typeClass = computed(() => `type-${props.material.material_type || 0}`)
const typeLabel = computed(() => MATERIAL_TYPE_LABEL[props.material.material_type] || '资料')
const highlightedName = computed(() => highlightText(props.material.material_name || '', props.keyword))

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[char]))
}

function highlightText(text, keyword) {
  if (!keyword || keyword.length < 2) return escapeHtml(text)
  const escaped = escapeHtml(text)
  const pattern = escapeHtml(keyword).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return escaped.replace(new RegExp(pattern, 'gi'), match => `<mark>${match}</mark>`)
}

function formatTime(time) {
  if (!time) return '刚刚'
  const diff = Date.now() - new Date(time).getTime()
  if (diff < 3600000) return `${Math.max(1, Math.floor(diff / 60000))} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)} 天前`
  return new Date(time).toLocaleDateString('zh-CN')
}

function formatNumber(num) {
  if (!num) return '0'
  if (num >= 10000) return `${(num / 10000).toFixed(1)}万`
  return String(num)
}
</script>

<style scoped>
.material-card-pro {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto;
  gap: var(--spacing-md);
  align-items: center;
  min-height: 112px;
  padding: var(--spacing-lg);
  border-radius: 24px;
  background: var(--color-bg-card);
  border: 1px solid rgba(154, 66, 45, 0.08);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.material-card-pro:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.material-card-pro__icon {
  width: 58px;
  height: 58px;
  border-radius: 18px;
  color: #fff;
  background: linear-gradient(135deg, #e87452, #f4b24f);
  display: flex;
  align-items: center;
  justify-content: center;
}

.material-card-pro__icon.type-2 { background: linear-gradient(135deg, #c98272, #e87452); }
.material-card-pro__icon.type-3 { background: linear-gradient(135deg, #86a96f, #d89334); }
.material-card-pro__icon.type-4 { background: linear-gradient(135deg, #f4b24f, #e87452); }
.material-card-pro__icon.type-5 { background: linear-gradient(135deg, #9b7cf4, #c98272); }
.material-card-pro__icon.type-6 { background: linear-gradient(135deg, #8b6f55, #b56b48); }

.material-card-pro__body {
  min-width: 0;
}

.material-card-pro__title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: 10px;
}

.material-card-pro h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-card-pro h3 :deep(mark) {
  color: var(--color-primary-dark);
  background: rgba(232, 116, 82, 0.22);
  border-radius: 4px;
  padding: 0 2px;
}

.material-card-pro__meta {
  display: flex;
  align-items: center;
  gap: 8px 14px;
  flex-wrap: wrap;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.material-card-pro__meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.material-card-pro__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

@media (max-width: 900px) {
  .material-card-pro {
    grid-template-columns: 52px minmax(0, 1fr);
  }

  .material-card-pro__actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}
</style>
