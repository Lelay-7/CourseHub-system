<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="个人中心" subtitle="查看你的选课、资料、积分和互动记录。" />

      <section class="profile-dashboard">
        <Card class="profile-card">
          <el-upload :show-file-list="false" :before-upload="beforeAvatarUpload" accept="image/*">
            <el-avatar :size="72" :src="userStore.userInfo?.avatar_url">
              <el-icon :size="32"><User /></el-icon>
            </el-avatar>
          </el-upload>
          <div>
            <h2>{{ userStore.userInfo?.nickname || '微信用户' }}</h2>
            <p>{{ roleLabel }} · {{ userStore.userInfo?.department || '计算机学院' }}</p>
            <span>{{ userStore.userInfo?.username || '2024051224' }} · 2024 级</span>
          </div>
          <div class="profile-card__actions">
            <el-button plain :icon="EditPen" @click="$router.push('/profile/settings')">编辑资料</el-button>
            <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
          </div>
        </Card>

        <Card class="profile-metrics">
          <div class="metric-main">
            <span>可用积分</span>
            <strong>{{ userStore.scoreBalance }}</strong>
          </div>
          <div class="metric-grid">
            <div><strong>{{ userStore.userInfo?.total_score || 820 }}</strong><span>累计积分</span></div>
            <div><strong>126</strong><span>本月获得</span></div>
            <div><strong>68</strong><span>本月消耗</span></div>
            <div><strong>23</strong><span>本月活跃</span></div>
          </div>
          <el-button type="primary" plain @click="$router.push('/profile/score')">查看明细</el-button>
        </Card>
      </section>

      <section class="quick-grid">
        <button v-for="item in quickEntries" :key="item.label" type="button" class="quick-card" @click="$router.push(item.path)">
          <span><el-icon :size="24"><component :is="item.icon" /></el-icon></span>
          <strong>{{ item.label }}</strong>
        </button>
      </section>

      <section class="recent-panel">
        <SectionTitle title="最近行为" subtitle="选课、下载、收藏和发帖会在这里汇总。" />
        <div class="recent-grid">
          <article v-for="item in recentItems" :key="item.title" class="recent-card">
            <StatusTag>{{ item.type }}</StatusTag>
            <strong>{{ item.title }}</strong>
            <span>{{ item.time }}</span>
          </article>
        </div>
      </section>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User,
  EditPen,
  Reading,
  UploadFilled,
  Download,
  Star,
  Present,
  Service,
  ChatDotRound
} from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Card from '@/components/Card.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useUserStore } from '@/stores/user'
import { USER_TYPE_LABEL } from '@/utils/constants'
import * as authApi from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()
const roleLabel = computed(() => USER_TYPE_LABEL[userStore.userType] || '学生')

const quickEntries = [
  { label: '我的课程', path: '/course', icon: Reading },
  { label: '我的上传', path: '/material', icon: UploadFilled },
  { label: '我的下载', path: '/material', icon: Download },
  { label: '我的收藏', path: '/profile/favorites', icon: Star },
  { label: '积分商城', path: '/mall', icon: Present },
  { label: '意见反馈', path: '/profile/feedback', icon: EditPen },
  { label: '联系客服', path: '/profile/support', icon: Service }
]

const recentItems = [
  { type: '最近选课', title: 'Web 前端工程实践', time: '今天 12:20' },
  { type: '最近下载', title: 'Vue3 组件设计实验指导书', time: '昨天 18:40' },
  { type: '最近收藏', title: '数据库系统原理', time: '2 天前' },
  { type: '最近发帖', title: '分享了数据库复习建议', time: '3 天前' }
]

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (event) => resolve(event.target.result)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

async function beforeAvatarUpload(file) {
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像文件不能超过 2MB')
    return false
  }
  try {
    const avatarUrl = await readFileAsDataUrl(file)
    const res = await authApi.uploadAvatar({ avatar_url: avatarUrl, file_name: file.name })
    userStore.updateUserInfo({ avatar_url: res.data?.avatar_url || avatarUrl })
    ElMessage.success('头像上传成功')
  } catch {
    ElMessage.warning('头像上传失败，请稍后重试')
  }
  return false
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录吗？', '操作确认', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    userStore.logout()
    router.push('/login')
  } catch {
    // cancelled
  }
}
</script>

<style scoped>
.profile-dashboard {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.profile-card,
.profile-metrics {
  padding: var(--spacing-xl);
}

.profile-card {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: var(--spacing-md);
  align-items: start;
}

.profile-card h2,
.profile-card p,
.profile-card span {
  display: block;
  margin: 0;
}

.profile-card h2 {
  color: var(--color-text-primary);
  font-size: 24px;
  font-weight: 900;
}

.profile-card p {
  margin-top: 6px;
  color: var(--color-text-regular);
}

.profile-card span {
  margin-top: 4px;
  color: var(--color-text-secondary);
}

.profile-card__actions {
  grid-column: 1 / -1;
  display: flex;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-lg);
}

.profile-metrics {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) auto;
  gap: var(--spacing-lg);
  align-items: center;
}

.metric-main {
  padding: var(--spacing-lg);
  border-radius: 22px;
  background: var(--color-bg-soft);
  text-align: center;
}

.metric-main span,
.metric-main strong,
.metric-grid span,
.metric-grid strong {
  display: block;
}

.metric-main span,
.metric-grid span {
  color: var(--color-text-secondary);
}

.metric-main strong {
  color: var(--color-primary);
  font-size: 38px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-sm);
}

.metric-grid div {
  padding: var(--spacing-md);
  border-radius: 18px;
  background: rgba(255, 240, 228, 0.72);
  text-align: center;
}

.metric-grid strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-xl);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
}

.quick-card {
  min-height: 116px;
  border: 1px solid rgba(154, 66, 45, 0.08);
  border-radius: 24px;
  background: var(--color-bg-card);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
}

.quick-card span {
  width: 46px;
  height: 46px;
  margin: 0 auto 10px;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(135deg, #f07462, #f8b25f);
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-card strong {
  color: var(--color-text-primary);
}

.recent-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--spacing-md);
}

.recent-card {
  padding: var(--spacing-lg);
  border-radius: 24px;
  background: var(--color-bg-card);
  border: 1px solid rgba(154, 66, 45, 0.08);
  box-shadow: var(--shadow-sm);
}

.recent-card strong,
.recent-card span {
  display: block;
}

.recent-card strong {
  margin-top: var(--spacing-md);
  color: var(--color-text-primary);
}

.recent-card span {
  margin-top: 4px;
  color: var(--color-text-secondary);
}

@media (max-width: 1000px) {
  .profile-dashboard,
  .profile-metrics,
  .quick-grid,
  .recent-grid {
    grid-template-columns: 1fr;
  }
}
</style>
