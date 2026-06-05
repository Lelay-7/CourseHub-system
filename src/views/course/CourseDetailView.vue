<template>
  <AppLayout>
    <div class="page-content course-detail-page">
      <PageHeader title="课程详情" :show-back="true">
        <template #extra>
          <el-button
            :icon="course.is_favorited ? StarFilled : Star"
            :type="course.is_favorited ? 'warning' : 'default'"
            @click="handleFavorite"
          >
            {{ course.is_favorited ? '已收藏' : '收藏' }}
          </el-button>
          <el-button :icon="Share" @click="handleShare">分享</el-button>
        </template>
      </PageHeader>

      <div v-if="loading" class="detail-loading">
        <LoadingSkeleton type="list" :rows="6" />
      </div>

      <template v-else-if="course.course_id">
        <!-- 基本信息 -->
        <div class="detail-section card">
          <div class="course-header">
            <img
              :src="course.cover_url || defaultCover"
              :alt="course.course_name"
              class="course-cover-img"
            />
            <div class="course-header-info">
              <h2>{{ course.course_name }}</h2>
              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">课程代码</span>
                  <span>{{ course.course_code }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">授课教师</span>
                  <span>{{ course.teacher_name }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">学分</span>
                  <span>{{ course.credit }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">上课时间</span>
                  <span>{{ course.schedule }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">上课地点</span>
                  <span>{{ course.location || '待定' }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">课程类型</span>
                  <el-tag :type="course.course_type === 1 ? 'danger' : 'primary'" size="small">
                    {{ course.course_type === 1 ? '必修' : '选修' }}
                  </el-tag>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 课程大纲 -->
        <div class="detail-section card">
          <h3 class="section-title">课程大纲</h3>
          <div
            class="course-outline"
            v-html="sanitizedOutline"
          />
          <div v-if="!course.outline" class="text-muted">暂无大纲</div>
        </div>

        <!-- 课程评价 -->
        <div class="detail-section card">
          <h3 class="section-title">
            课程评价
            <span class="avg-rating">
              <StarRating :model-value="course.avg_rating" :readonly="true" :show-text="true" />
            </span>
          </h3>
          <div v-if="reviews.length > 0" class="review-list">
            <div v-for="review in reviews" :key="review.review_id" class="review-item">
              <div class="review-header flex-between">
                <span class="review-user">{{ maskName(review.user_name) }}</span>
                <StarRating :model-value="review.rating" :readonly="true" :size="14" />
              </div>
              <p class="review-content">{{ review.content }}</p>
              <span class="review-time">{{ formatTime(review.create_time) }}</span>
            </div>
          </div>
          <EmptyState v-else description="暂无评价" />
        </div>

        <!-- 相关课程推荐 -->
        <div class="detail-section" v-if="relatedCourses.length > 0">
          <h3 class="section-title">相关课程推荐</h3>
          <div class="course-grid">
            <CourseCard
              v-for="rc in relatedCourses"
              :key="rc.course_id"
              :course="rc"
            />
          </div>
        </div>
      </template>

      <ErrorState v-else title="课程不存在" @retry="fetchDetail" />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, StarFilled, Share } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import AppLayout from '@/components/AppLayout.vue'
import PageHeader from '@/components/PageHeader.vue'
import CourseCard from '@/components/CourseCard.vue'
import StarRating from '@/components/StarRating.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import ErrorState from '@/components/ErrorState.vue'
import * as courseApi from '@/api/course'
import * as favoriteApi from '@/api/favorite'

const route = useRoute()
const courseId = computed(() => route.params.id)

const course = ref({})
const reviews = ref([])
const relatedCourses = ref([])
const loading = ref(true)

const defaultCover = new URL('@/assets/images/default-cover.png', import.meta.url).href

const sanitizedOutline = computed(() => {
  if (!course.value.outline) return ''
  return DOMPurify.sanitize(course.value.outline, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 'h1', 'h2', 'h3', 'h4', 'ul', 'ol', 'li', 'a', 'img', 'table', 'tr', 'td', 'th', 'thead', 'tbody'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'class', 'target']
  })
})

async function fetchDetail() {
  loading.value = true
  try {
    const [detailRes] = await Promise.all([
      courseApi.getCourseDetail(courseId.value)
    ])
    if (detailRes.code === 200) {
      course.value = detailRes.data || {}
      reviews.value = detailRes.data?.reviews || []
      relatedCourses.value = (detailRes.data?.related_courses || []).slice(0, 6)
    }
  } catch { /* handled */ }
  finally { loading.value = false }
}

async function handleFavorite() {
  try {
    if (course.value.is_favorited) {
      await favoriteApi.removeFavorite({ type: 'course', target_id: course.value.course_id })
      course.value.is_favorited = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteApi.addFavorite({ type: 'course', target_id: course.value.course_id })
      course.value.is_favorited = true
      ElMessage.success('收藏成功')
    }
  } catch { /* handled */ }
}

function handleShare() {
  const url = `${window.location.origin}/course/${courseId.value}`
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('链接已复制，可分享给好友')
  }).catch(() => {
    ElMessage.info(`分享链接: ${url}`)
  })
}

function maskName(name) {
  if (!name) return '匿名'
  if (name.length <= 1) return name
  return name[0] + '**'
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleDateString('zh-CN')
}

onMounted(fetchDetail)
</script>

<style scoped>
.detail-loading { padding: var(--spacing-lg); }

.detail-section {
  margin-bottom: var(--spacing-lg);
}

.course-header {
  display: flex;
  gap: var(--spacing-lg);
}
.course-cover-img {
  width: 320px;
  height: 180px;
  border-radius: var(--radius-md);
  object-fit: cover;
  flex-shrink: 0;
}
.course-header-info h2 {
  margin: 0 0 var(--spacing-md);
  font-size: var(--font-size-xl);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-sm);
}
.info-item {
  display: flex;
  gap: var(--spacing-sm);
}
.info-label {
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.section-title {
  margin: 0 0 var(--spacing-md);
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.course-outline {
  line-height: 1.8;
  color: var(--color-text-regular);
}
.text-muted { color: var(--color-text-placeholder); }

.avg-rating {
  font-size: var(--font-size-base);
}

.review-item {
  padding: var(--spacing-md) 0;
  border-bottom: 1px solid var(--color-border-light);
}
.review-item:last-child { border-bottom: none; }
.review-user {
  font-weight: 500;
  color: var(--color-text-primary);
}
.review-content {
  margin: var(--spacing-sm) 0;
  color: var(--color-text-regular);
  font-size: var(--font-size-sm);
}
.review-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-md);
}

@media (max-width: 767px) {
  .course-header {
    flex-direction: column;
  }
  .course-cover-img {
    width: 100%;
    height: auto;
    aspect-ratio: 16/9;
  }
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
