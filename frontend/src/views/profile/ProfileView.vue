<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="个人中心" :subtitle="userStore.isAdmin ? '系统管理与维护' : '查看你的选课、资料、积分和互动记录。'" />

      <section class="profile-dashboard">
        <Card class="profile-card">
          <input ref="avatarInput" type="file" accept="image/*" style="display:none" @change="onAvatarChange" />
          <el-avatar :size="72" :src="userStore.userInfo?.avatar_url" style="cursor:pointer" @click="$refs.avatarInput.click()">
            <el-icon :size="32"><User /></el-icon>
          </el-avatar>
          <div>
            <h2>{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '未命名' }}</h2>
            <p>
              {{ roleLabel }}
              <template v-if="userStore.isTeacher && userStore.userInfo?.title"> · {{ userStore.userInfo.title }}</template>
              {{ ' · ' }}{{ userStore.userInfo?.department || '' }}
            </p>
            <span v-if="userStore.isStudent || userStore.isTeacher">
              {{ userStore.userInfo?.username || '' }}
              <template v-if="userStore.userInfo?.class_name"> · {{ userStore.userInfo?.class_name }}</template>
            </span>
            <span v-if="userStore.isAdmin">管理员账号 · 系统维护</span>
            <template v-if="userStore.isTeacher && userStore.userInfo?.office_location">
              <br /><span>办公室: {{ userStore.userInfo?.office_location }}</span>
            </template>
          </div>
          <div class="profile-card__actions">
            <el-button plain :icon="EditPen" @click="$router.push('/profile/edit')">编辑资料</el-button>
            <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
          </div>
        </Card>

        <Card v-if="!userStore.isAdmin" class="profile-metrics">
          <div class="metric-main">
            <span>可用积分</span>
            <strong>{{ userStore.scoreBalance }}</strong>
          </div>
          <div class="metric-grid">
            <div><strong>{{ userStore.userInfo?.total_score || 0 }}</strong><span>累计积分</span></div>
            <div><strong>{{ monthStats.earned }}</strong><span>本月获得</span></div>
            <div><strong>{{ monthStats.spent }}</strong><span>本月消耗</span></div>
            <div><strong>{{ monthStats.active }}</strong><span>本月活跃</span></div>
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

      <section v-if="!userStore.isAdmin" class="recent-panel">
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
import { computed, ref, reactive, onMounted } from 'vue'
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
  ShoppingCart,
  Service,
  ChatDotRound,
  Monitor,
  HomeFilled,
  Setting,
  UserFilled
} from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Card from '@/components/Card.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useUserStore } from '@/stores/user'
import { useSelectionStore } from '@/stores/selection'
import { USER_TYPE_LABEL } from '@/utils/constants'
import request from '@/api/request'
import * as authApi from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()
const selectionStore = useSelectionStore()
const roleLabel = computed(() => USER_TYPE_LABEL[userStore.userType] || '学生')

const quickEntries = computed(() => {
  if (userStore.isAdmin) {
    return [
      { label: '管理后台', path: '/admin/dashboard', icon: Monitor },
      { label: '前台首页', path: '/home', icon: HomeFilled },
      { label: '课程管理', path: '/admin/courses', icon: Reading },
      { label: '资料审核', path: '/admin/materials', icon: UploadFilled },
      { label: '评价管理', path: '/admin/reviews', icon: ChatDotRound },
      { label: '广场管理', path: '/admin/square', icon: EditPen },
      { label: '用户管理', path: '/admin/users', icon: User },
      { label: '商品管理', path: '/admin/goods', icon: ShoppingCart },
      { label: '订单管理', path: '/admin/orders', icon: Present },
      { label: 'Banner', path: '/admin/banners', icon: Setting },
      { label: '接受反馈', path: '/profile/feedback', icon: EditPen },
      { label: '系统设置', path: '/profile/settings', icon: Service }
    ]
  }
  // 学生/教师
  return [
    { label: '我的选课', path: '/profile/courses', icon: Reading },
    { label: '我的好友', path: '/contact?tab=friends', icon: UserFilled },
    { label: '我的上传', path: '/material?tab=my', icon: UploadFilled },
    { label: '我的下载', path: '/profile/downloads', icon: Download },
    { label: '我的收藏', path: '/profile/favorites', icon: Star },
    { label: '我的评价', path: '/profile/reviews', icon: ChatDotRound },
    { label: '我的订单', path: '/profile/orders', icon: Present },
    { label: '积分商城', path: '/mall', icon: ShoppingCart },
    { label: '意见反馈', path: '/profile/feedback', icon: EditPen },
    { label: '联系客服', path: '/profile/support', icon: Service },
    { label: '系统设置', path: '/profile/settings', icon: Setting }
  ]
})

const recentItems = ref([])
const monthStats = reactive({ earned: 0, spent: 0, active: 0 })

async function fetchMonthStats() {
  try {
    const res = await request.get('/api/score/details', { params: { page: 1, pageSize: 100 } })
    const data = res.data?.list || res.data?.records || []
    const now = new Date()
    const thisMonth = data.filter(item => {
      const t = new Date(item.create_time || item.createTime)
      return t.getMonth() === now.getMonth() && t.getFullYear() === now.getFullYear()
    })
    monthStats.earned = thisMonth.filter(i => (i.change_amount || i.changeAmount) > 0)
      .reduce((s, i) => s + (i.change_amount || i.changeAmount), 0)
    monthStats.spent = Math.abs(thisMonth.filter(i => (i.change_amount || i.changeAmount) < 0)
      .reduce((s, i) => s + (i.change_amount || i.changeAmount), 0))
    monthStats.active = new Set(thisMonth.map(i => i.source_desc || i.sourceDesc)).size
  } catch { /* ignore */ }
}

async function fetchRecentItems() {
  const items = []
  // 选课记录
  if (selectionStore.selectedIds.length > 0) {
    items.push({ type: '最近选课', title: `已选 ${selectionStore.count} 门课程`, time: '' })
  }
  try {
    const res = await request.get('/api/score/details', { params: { page: 1, pageSize: 4 } })
    const data = res.data?.list || res.data?.records || []
    data.forEach(item => {
      items.push({
        type: item.source_desc?.includes('下载') ? '最近下载' :
              item.source_desc?.includes('评价') ? '最近评价' :
              item.source_desc?.includes('上传') ? '最近上传' :
              item.source_desc?.includes('兑换') ? '最近兑换' : '最近行为',
        title: item.source_desc || '',
        time: formatRelativeTime(item.create_time)
      })
    })
  } catch { /* ignore */ }
  recentItems.value = items.slice(0, 4)
}

function formatRelativeTime(time) {
  if (!time) return ''
  const diff = Date.now() - new Date(time).getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff/60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff/3600000)} 小时前`
  return `${Math.floor(diff/86400000)} 天前`
}

function onAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像文件不能超过 2MB')
    return
  }
  authApi.uploadAvatar(file).then(res => {
    userStore.updateUserInfo({ avatar_url: `${res.data?.avatar_url}?v=${Date.now()}` })
    ElMessage.success('头像上传成功')
  }).catch(() => {
    ElMessage.warning('头像上传失败，请稍后重试')
  })
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

onMounted(() => {
  userStore.fetchUserInfo() // 刷新积分
  if (!userStore.isAdmin) {
    fetchRecentItems()
    fetchMonthStats()
  }
})
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
  background: color-mix(in srgb, var(--color-bg-card) 72%, transparent);
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
  border: 1px solid color-mix(in srgb, var(--color-primary) 8%, transparent);
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
  color: var(--color-on-primary);
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
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
  border: 1px solid color-mix(in srgb, var(--color-primary) 8%, transparent);
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
