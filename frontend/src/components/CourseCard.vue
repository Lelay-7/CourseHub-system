<template>
  <div class="course-card card card-hover" @click="goDetail">
    <!-- 封面图：16:9 -->
    <div class="course-cover">
      <img
        :src="course.cover_url || defaultCover"
        :alt="course.course_name"
        class="img-cover"
        @error="onImgError"
      />
      <!-- 收藏按钮 -->
      <el-button
        class="favorite-btn"
        :icon="course.is_favorited ? StarFilled : Star"
        :type="course.is_favorited ? 'warning' : 'default'"
        circle
        size="small"
        @click.stop="toggleFavorite"
      />
      <button
        v-if="selectable"
        type="button"
        class="compare-toggle"
        :class="{ selected }"
        @click.stop="toggleCompare"
      >
        {{ selected ? '已加入对比' : '加入对比' }}
      </button>
    </div>

    <div class="course-info">
      <h3 class="course-name text-ellipsis" v-html="highlightedName" />
      <div class="course-meta">
        <span class="meta-item">
          <el-icon><User /></el-icon>
          {{ course.teacher_name || '未知教师' }}
        </span>
        <span class="meta-item">
          <el-icon><UserFilled /></el-icon>
          {{ formatNumber(course.student_count) }}人选课
        </span>
      </div>
      <div class="course-footer">
        <el-tag :type="courseTypeTag" size="small">
          {{ courseTypeLabel }}
        </el-tag>
        <span class="course-credit">{{ course.credit }} 学分</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Star, StarFilled, User, UserFilled } from '@element-plus/icons-vue'
import { COURSE_TYPE, COURSE_TYPE_LABEL } from '@/utils/constants'

const props = defineProps({
  course: { type: Object, required: true },
  keyword: { type: String, default: '' },
  selectable: { type: Boolean, default: false },
  selected: { type: Boolean, default: false }
})

const emit = defineEmits(['favorite', 'compare-toggle'])
const router = useRouter()

const defaultCover = new URL('@/assets/images/default-cover.svg', import.meta.url).href

const courseTypeLabel = computed(() => COURSE_TYPE_LABEL[props.course.course_type] || '')
const courseTypeTag = computed(() =>
  props.course.course_type === COURSE_TYPE.REQUIRED ? 'danger' : 'primary'
)
const highlightedName = computed(() => highlightText(props.course.course_name || '', props.keyword))

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

function formatNumber(num) {
  if (!num) return '0'
  if (num >= 10000) return (num / 10000).toFixed(1) + '万'
  return String(num)
}

function goDetail() {
  router.push(`/course/${props.course.course_id}`)
}

function toggleFavorite() {
  emit('favorite', props.course)
}

function toggleCompare() {
  emit('compare-toggle', props.course)
}

function onImgError(e) {
  e.target.src = defaultCover
}
</script>

<style scoped>
.course-card {
  cursor: pointer;
  overflow: hidden;
  padding: 0;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.course-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  padding-top: 0;
  background: var(--color-bg-page);
  overflow: hidden;
}

.course-cover img {
  position: absolute;
  inset: 0;
  transition: transform var(--transition-slow);
}

.course-card:hover .course-cover img {
  transform: scale(1.05);
}

.favorite-btn {
  position: absolute;
  top: var(--spacing-sm);
  right: var(--spacing-sm);
  z-index: 1;
  backdrop-filter: blur(10px);
  background: rgba(255, 253, 248, 0.9);
}

.compare-toggle {
  position: absolute;
  left: var(--spacing-sm);
  bottom: var(--spacing-sm);
  z-index: 1;
  border: 0;
  border-radius: 999px;
  padding: 6px 10px;
  color: var(--color-primary-dark);
  background: rgba(255, 253, 248, 0.9);
  backdrop-filter: blur(10px);
  cursor: pointer;
  font-size: var(--font-size-xs);
  font-weight: 800;
  box-shadow: 0 0 0 1px rgba(22, 105, 122, 0.12) inset;
}

.compare-toggle.selected {
  color: #fff;
  background: var(--color-accent);
  box-shadow: 0 8px 18px rgba(239, 131, 84, 0.28);
}

.course-info {
  padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
  min-height: 112px;
}

.course-name {
  margin: 0 0 var(--spacing-sm);
  font-size: var(--font-size-base);
  font-weight: 700;
  color: var(--color-text-primary);
}

.course-name :deep(mark) {
  color: var(--color-primary-dark);
  background: rgba(239, 131, 84, 0.28);
  border-radius: 3px;
  padding: 0 2px;
}

.course-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
  min-width: 0;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.course-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.course-credit {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  background: var(--color-bg-soft);
  border-radius: 999px;
  padding: 3px 8px;
}
</style>
