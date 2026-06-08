<template>
  <AppLayout>
    <PageContainer>
      <section class="home-welcome">
        <div>
          <p>Hi，{{ userStore.userInfo?.nickname || '同学' }}</p>
          <h1>不高山选课通</h1>
          <span>完成课程评价、上传资料和参与讨论都可以获得积分，积分可用于资料下载和商城兑换。</span>
        </div>
        <strong>{{ userStore.scoreBalance }} 积分</strong>
      </section>

      <div class="home-search">
        <el-input
          v-model="keyword"
          size="large"
          placeholder="请输入关键词查找课程、资料、老师"
          :prefix-icon="Search"
          clearable
          @keyup.enter="goSearch"
        />
        <el-button type="primary" size="large" @click="goSearch">搜索</el-button>
      </div>

      <section class="home-promo">
        <div>
          <StatusTag>资料有偿收集</StatusTag>
          <h2>上传优质复习资料，兑换更多学习权益</h2>
          <p>课程评价、资料下载、同学讨论和积分兑换都可以在这里串起来。</p>
        </div>
        <el-button type="primary" plain @click="$router.push('/material')">去上传资料</el-button>
      </section>

      <section class="feature-grid">
        <button
          v-for="item in featureEntries"
          :key="item.path"
          type="button"
          class="feature-item"
          @click="$router.push(item.path)"
        >
          <span :style="{ background: item.bg }">
            <el-icon :size="28"><component :is="item.icon" /></el-icon>
          </span>
          <strong>{{ item.label }}</strong>
          <small>{{ item.desc }}</small>
        </button>
      </section>

      <section class="home-section">
        <SectionTitle title="热门课程">
          <el-button text type="primary" @click="$router.push('/course')">查看更多</el-button>
        </SectionTitle>
        <div class="mini-card-grid">
          <article v-for="course in hotCourses" :key="course.course_id" class="mini-card" @click="$router.push(`/course/${course.course_id}`)">
            <StatusTag>{{ course.course_type === 1 ? '必修' : '选修' }}</StatusTag>
            <strong>{{ course.course_name }}</strong>
            <span>{{ course.teacher_name }} · {{ course.credit }} 学分</span>
          </article>
        </div>
      </section>

      <section class="home-section">
        <SectionTitle title="最新资料">
          <el-button text type="primary" @click="$router.push('/material')">查看更多</el-button>
        </SectionTitle>
        <div class="mini-card-grid">
          <article v-for="material in hotMaterials" :key="material.material_id" class="mini-card" @click="$router.push(`/material/${material.material_id}`)">
            <StatusTag :tone="material.download_score ? 'warning' : 'success'">
              {{ material.download_score ? `${material.download_score} 积分` : '免费' }}
            </StatusTag>
            <strong>{{ material.material_name }}</strong>
            <span>{{ material.uploader_name }} · {{ material.download_count }} 次下载</span>
          </article>
        </div>
      </section>

      <section class="home-section">
        <SectionTitle title="同学动态" />
        <div class="activity-list">
          <div v-for="item in activities" :key="item.text" class="activity-item">
            <span>{{ item.name.slice(0, 1) }}</span>
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.text }}</p>
            </div>
          </div>
        </div>
      </section>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search,
  Reading,
  ChatDotRound,
  FolderOpened,
  UserFilled,
  Present
} from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useUserStore } from '@/stores/user'
import * as courseApi from '@/api/course'
import * as materialApi from '@/api/material'

const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')
const hotCourses = ref([])
const hotMaterials = ref([])

const featureEntries = [
  { label: '选课中心', desc: '筛选、对比、选择', path: '/course', icon: Reading, bg: 'linear-gradient(135deg, #f07462, #f8b25f)' },
  { label: '同学广场', desc: '经验与问答', path: '/square', icon: ChatDotRound, bg: 'linear-gradient(135deg, #c98272, #e87452)' },
  { label: '学习资料', desc: '预览与下载', path: '/material', icon: FolderOpened, bg: 'linear-gradient(135deg, #f4b24f, #e87452)' },
  { label: '师生通讯录', desc: '课程联系人', path: '/contact', icon: UserFilled, bg: 'linear-gradient(135deg, #86a96f, #d89334)' },
  { label: '积分商城', desc: '兑换学习权益', path: '/mall', icon: Present, bg: 'linear-gradient(135deg, #9b7cf4, #e87452)' }
]

const activities = [
  { name: '张义彬', text: '收藏了 Web 前端工程实践，并查看了对应实验指导书。' },
  { name: '刘青', text: '在同学广场分享了数据库系统原理的复习建议。' },
  { name: '李欣', text: '上传了软件工程答辩 PPT 范例，获得积分奖励。' }
]

function goSearch() {
  const q = keyword.value.trim()
  if (!q) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  router.push({ path: '/course', query: { keyword: q } })
}

async function fetchHomeData() {
  const [courseRes, materialRes] = await Promise.all([
    courseApi.getCourseList({ page: 1, pageSize: 3, sortBy: 'rating' }),
    materialApi.getMaterialList({ page: 1, pageSize: 3, sortBy: 'create_time' })
  ])
  if (courseRes.code === 200) hotCourses.value = courseRes.data?.list || courseRes.data?.records || []
  if (materialRes.code === 200) hotMaterials.value = materialRes.data?.list || materialRes.data?.records || []
}

onMounted(() => {
  fetchHomeData().catch(() => {})
})
</script>

<style scoped>
.home-welcome {
  min-height: 132px;
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-xl);
  border-radius: 28px;
  color: #fffaf3;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-lg);
  background:
    radial-gradient(circle at 12% 12%, rgba(255,255,255,.18), transparent 24%),
    linear-gradient(135deg, #ff7f73 0%, #ef6c5a 100%);
  box-shadow: var(--shadow-md);
}

.home-welcome p,
.home-welcome h1,
.home-welcome span {
  margin: 0;
}

.home-welcome p {
  color: rgba(255, 250, 243, 0.78);
}

.home-welcome h1 {
  margin-top: 4px;
  font-size: 32px;
  font-weight: 900;
}

.home-welcome span {
  display: block;
  max-width: 680px;
  margin-top: 8px;
  color: rgba(255, 250, 243, 0.84);
}

.home-welcome strong {
  flex: 0 0 auto;
  padding: 8px 14px;
  border-radius: 999px;
  color: var(--color-primary-dark);
  background: rgba(255, 250, 243, 0.92);
}

.home-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-md);
  border-radius: 26px;
  background: rgba(255, 250, 243, 0.96);
  box-shadow: var(--shadow-sm);
}

.home-promo {
  min-height: 174px;
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-xl);
  border-radius: 28px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--spacing-lg);
  color: #fffaf3;
  background: linear-gradient(135deg, #e87452 0%, #f4b24f 58%, #c98272 100%);
  box-shadow: var(--shadow-md);
}

.home-promo h2 {
  max-width: 560px;
  margin: var(--spacing-sm) 0;
  font-size: 28px;
  line-height: 1.25;
}

.home-promo p {
  margin: 0;
  color: rgba(255, 250, 243, 0.82);
}

.feature-grid,
.mini-card-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--spacing-lg);
}

.feature-grid {
  margin-bottom: var(--spacing-xl);
}

.feature-item,
.mini-card,
.activity-item {
  border: 1px solid rgba(154, 66, 45, 0.08);
  border-radius: 24px;
  background: var(--color-bg-card);
  box-shadow: var(--shadow-sm);
}

.feature-item {
  min-height: 132px;
  padding: var(--spacing-md);
  cursor: pointer;
}

.feature-item span {
  width: 56px;
  height: 56px;
  margin: 0 auto 10px;
  border-radius: 18px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.feature-item strong,
.feature-item small,
.mini-card strong,
.mini-card span {
  display: block;
}

.feature-item strong {
  color: var(--color-text-primary);
}

.feature-item small,
.mini-card span {
  margin-top: 5px;
  color: var(--color-text-secondary);
}

.home-section {
  margin-bottom: var(--spacing-xl);
}

.mini-card-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.mini-card {
  padding: var(--spacing-lg);
  cursor: pointer;
}

.mini-card strong {
  margin-top: var(--spacing-md);
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-list {
  display: grid;
  gap: var(--spacing-md);
}

.activity-item {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: var(--spacing-md);
  padding: var(--spacing-md);
}

.activity-item > span {
  width: 44px;
  height: 44px;
  border-radius: 16px;
  color: #fff;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
}

.activity-item strong,
.activity-item p {
  display: block;
  margin: 0;
}

.activity-item p {
  margin-top: 4px;
  color: var(--color-text-secondary);
}

@media (max-width: 1000px) {
  .feature-grid,
  .mini-card-grid {
    grid-template-columns: 1fr;
  }

  .home-search {
    grid-template-columns: 1fr;
  }
}
</style>
