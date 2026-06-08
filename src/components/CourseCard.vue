<template>
  <article class="course-card-pro">
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
      <span><el-icon><UserFilled /></el-icon>{{ formatNumber(course.student_count) }} 人已选</span>
    </div>

    <div class="course-card-pro__links">
      <button type="button" @click="$router.push(`/material?course=${course.course_id}`)">查看资料</button>
      <button type="button" @click="$router.push(`/square?course=${course.course_id}`)">查看讨论</button>
      <button type="button" @click="$router.push('/contact')">联系老师</button>
    </div>

    <div class="course-card-pro__actions">
      <el-button plain @click="$router.push(`/course/${course.course_id}`)">查看详情</el-button>
      <el-button plain @click="$emit('compare-toggle', course)">加入对比</el-button>
      <el-button type="primary" :disabled="selectDisabled" @click="$emit('select-course', course)">
        {{ statusLabel === '已选' ? '已选择' : '选择课程' }}
      </el-button>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { User, UserFilled, Collection, Clock } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import { COURSE_TYPE, COURSE_TYPE_LABEL } from '@/utils/constants'

const props = defineProps({
  course: { type: Object, required: true },
  keyword: { type: String, default: '' }
})

defineEmits(['compare-toggle', 'select-course'])

const descriptions = {
  1: '围绕常用数据结构、复杂度分析和算法设计展开，适合需要打牢程序设计基础的同学。',
  2: '以 Vue3、组件拆分和前端工程实践为主线，强调可维护界面和项目交付能力。',
  3: '从关系模型、SQL 查询到索引优化，帮助你理解数据库系统在真实项目中的使用方式。',
  4: '覆盖机器学习基本流程、模型评估和小型实践任务，适合作为人工智能方向入门课。',
  5: '聚焦需求分析、项目计划、迭代管理和答辩表达，适合准备团队项目的同学。',
  6: '讲解常见网络攻击、防护思路和实验验证，适合希望补齐安全意识的同学。',
  7: '围绕移动端页面、接口联调和应用发布流程展开，强调完整应用开发体验。',
  8: '结合数字内容生产、交互媒体和作品展示，适合跨专业拓展与创意实践。'
}

const statusList = ['可选', '已选', '名额紧张', '时间冲突', '已满']
const statusLabel = computed(() => props.course.status || statusList[(props.course.course_id - 1) % statusList.length])
const statusTone = computed(() => ({
  可选: 'success',
  已选: 'default',
  名额紧张: 'warning',
  时间冲突: 'danger',
  已满: 'info'
}[statusLabel.value] || 'default'))
const selectDisabled = computed(() => ['已选', '时间冲突', '已满'].includes(statusLabel.value))
const courseTypeLabel = computed(() => COURSE_TYPE_LABEL[props.course.course_type] || '课程')
const typeTone = computed(() => props.course.course_type === COURSE_TYPE.REQUIRED ? 'danger' : 'default')
const courseDescription = computed(() => props.course.description || descriptions[props.course.course_id] || '课程信息已整理，可结合评价、资料和同学讨论进行选择。')
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

function formatNumber(num) {
  if (!num) return '0'
  if (num >= 10000) return `${(num / 10000).toFixed(1)}万`
  return String(num)
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
</style>
