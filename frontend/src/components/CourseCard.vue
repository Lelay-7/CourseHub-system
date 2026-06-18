<template>
  <article class="course-card-pro" :class="{ 'is-selected': isSelected, 'is-compared': isCompared }">
    <div class="course-card-pro__head">
      <StatusTag :tone="typeTone">{{ courseTypeLabel }}</StatusTag>
      <StatusTag :tone="statusTone">{{ statusLabel }}</StatusTag>
    </div>

    <h3 v-html="highlightedName" />
    <p>{{ courseDescription }}</p>

    <div class="course-card-pro__meta">
      <span><el-icon><User /></el-icon>{{ course.teacher_name || '未知教师' }}</span>
      <span><el-icon><Collection /></el-icon>{{ course.credit || 0 }} 学分</span>
      <span><el-icon><Clock /></el-icon>{{ course.schedule || '时间待定' }}</span>
      <span><el-icon><StarFilled /></el-icon>{{ course.avg_rating || '-' }} 分</span>
    </div>

    <div class="course-card-pro__links">
      <button type="button" @click="$router.push('/material')">查看资料</button>
      <button type="button" @click="$router.push('/square')">查看讨论</button>
      <button type="button" @click="$router.push({ path: '/contact', query: { keyword: course.teacher_name || '' } })">联系老师</button>
    </div>

    <div class="course-card-pro__actions">
      <el-button plain @click="$router.push(`/course/${courseId}`)">查看详情</el-button>
      <el-button v-if="showCompare" :type="isCompared ? 'warning' : 'default'" plain @click="$emit('compare-toggle', course)">
        {{ isCompared ? '✓ 已对比' : '加入对比' }}
      </el-button>
      <el-button :type="isSelected ? 'success' : 'primary'" @click="$emit('select-course', course)">
        {{ isSelected ? '✓ 已选择' : '选择课程' }}
      </el-button>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { User, StarFilled, Collection, Clock } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { COURSE_TYPE, COURSE_TYPE_LABEL } from '@/utils/constants'

const props = defineProps({
  course: { type: Object, required: true },
  keyword: { type: String, default: '' },
  isSelected: { type: Boolean, default: false },
  isCompared: { type: Boolean, default: false },
  showCompare: { type: Boolean, default: true }
})

defineEmits(['compare-toggle', 'select-course'])

// 兼容后端返回 courseId（驼峰）或 course_id（下划线）两种字段名
const courseId = computed(() => props.course.course_id ?? props.course.courseId ?? props.course.id)

const statusLabel = computed(() => props.course.status === 1 ? '可选' : '已结束')
const statusTone = computed(() => statusLabel.value === '可选' ? 'success' : 'info')
const selectDisabled = computed(() => statusLabel.value !== '可选')
const courseTypeLabel = computed(() => COURSE_TYPE_LABEL[props.course.course_type] || '课程')
const typeTone = computed(() => props.course.course_type === COURSE_TYPE.REQUIRED ? 'danger' : 'default')
const courseDescription = computed(() => props.course.description || '暂无课程介绍')
const highlightedName = computed(() => highlightText(props.course.course_name || '', props.keyword))

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
</script>

<style scoped>
.course-card-pro {
  display: flex;
  flex-direction: column;
  min-height: 306px;
  padding: 20px;
  border-radius: 24px;
  background: var(--color-bg-card);
  border: 1px solid rgba(154, 66, 45, 0.08);
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.course-card-pro:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.course-card-pro__head,
.course-card-pro__actions,
.course-card-pro__links {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.course-card-pro__head {
  justify-content: space-between;
  margin-bottom: var(--spacing-md);
}

.course-card-pro h3 {
  margin: 0 0 10px;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: 900;
  line-height: 1.35;
}

.course-card-pro h3 :deep(mark) {
  color: var(--color-primary-dark);
  background: rgba(232, 116, 82, 0.22);
  border-radius: 4px;
  padding: 0 2px;
}

.course-card-pro p {
  margin: 0;
  color: var(--color-text-regular);
  line-height: 1.7;
}

.course-card-pro__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  margin: var(--spacing-md) 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.course-card-pro__meta span {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.course-card-pro__links {
  margin-top: auto;
  padding-top: var(--spacing-sm);
}

.course-card-pro__links button {
  border: 0;
  padding: 0;
  color: #4c91d8;
  background: transparent;
  cursor: pointer;
  font-weight: 800;
}

.course-card-pro__actions {
  margin-top: var(--spacing-md);
}

.course-card-pro__actions :deep(.el-button) {
  flex: 1 1 auto;
  min-width: 90px;
}
.course-card-pro.is-selected {
  border-color: #67c23a;
  box-shadow: 0 0 0 2px rgba(103,194,58,.15);
}
.course-card-pro.is-compared {
  border-color: #e6a23c;
  box-shadow: 0 0 0 2px rgba(230,162,60,.15);
}
</style>
