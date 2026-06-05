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
    </div>

    <div class="course-info">
      <h3 class="course-name text-ellipsis">{{ course.course_name }}</h3>
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
  course: { type: Object, required: true }
})

const emit = defineEmits(['favorite'])
const router = useRouter()

const defaultCover = new URL('@/assets/images/default-cover.png', import.meta.url).href

const courseTypeLabel = computed(() => COURSE_TYPE_LABEL[props.course.course_type] || '')
const courseTypeTag = computed(() =>
  props.course.course_type === COURSE_TYPE.REQUIRED ? 'danger' : 'primary'
)

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

function onImgError(e) {
  e.target.src = defaultCover
}
</script>

<style scoped>
.course-card {
  cursor: pointer;
  overflow: hidden;
  padding: 0;
}

.course-cover {
  position: relative;
  width: 100%;
  padding-top: 56.25%; /* 16:9 */
  background: var(--color-bg-page);
  overflow: hidden;
}

.course-cover img {
  position: absolute;
  top: 0;
  left: 0;
}

.favorite-btn {
  position: absolute;
  top: var(--spacing-sm);
  right: var(--spacing-sm);
  z-index: 1;
}

.course-info {
  padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
}

.course-name {
  margin: 0 0 var(--spacing-sm);
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-text-primary);
}

.course-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
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
}
</style>
