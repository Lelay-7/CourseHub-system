<template>
  <AppLayout>
    <PageContainer class="profile-page">
      <PageHeader title="个人中心" :subtitle="userStore.isAdmin ? '管理工作与账号服务' : '你的学习记录、积分与账号服务'" />
      <section class="profile-overview">
        <div class="profile-identity card">
          <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarChange" />
          <button class="avatar-button" aria-label="更换头像" :disabled="avatarSaving" :aria-busy="avatarSaving" @click="$refs.avatarInput.click()"><el-avatar :size="64" :src="userStore.userInfo?.avatar_url"><el-icon :size="28"><User /></el-icon></el-avatar></button>
          <div class="profile-identity__copy"><h2>{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '未命名' }}</h2><p>{{ roleLabel }}<template v-if="userStore.userInfo?.department"> · {{ userStore.userInfo.department }}</template></p><span>{{ userStore.userInfo?.username }}</span></div>
          <router-link to="/profile/edit" class="text-link">编辑资料 <span aria-hidden="true">→</span></router-link>
          <div class="profile-role-detail" v-if="userStore.isTeacher && (userStore.userInfo?.title || userStore.userInfo?.office_location)">{{ [userStore.userInfo.title, userStore.userInfo.office_location].filter(Boolean).join(' · ') }}</div>
        </div>
        <div v-if="!userStore.isAdmin" class="profile-balance card">
          <div><span>可用积分</span><strong>{{ userStore.scoreBalance }}</strong></div>
          <router-link to="/profile/score" class="text-link">积分明细 <span aria-hidden="true">→</span></router-link>
          <p v-if="statsError" class="inline-status" role="alert">月度统计暂时无法加载。<button type="button" @click="fetchMonthStats">重试</button></p>
          <p v-else-if="statsLoading" class="inline-status" role="status">正在读取本月统计…</p>
          <dl v-else class="month-stats"><div><dt>本月获得</dt><dd>{{ monthStats.earned }}</dd></div><div><dt>本月消耗</dt><dd>{{ monthStats.spent }}</dd></div><div><dt>本月退回</dt><dd>{{ monthStats.refunded }}</dd></div></dl>
        </div>
      </section>

      <nav class="quick-grid" aria-label="常用功能">
        <router-link v-for="item in quickEntries" :key="item.path" :to="item.path" class="quick-entry"><el-icon :size="23"><component :is="item.icon" /></el-icon><strong>{{ item.label }}</strong><span>{{ item.description }}</span></router-link>
      </nav>

      <div class="profile-body">
        <div class="entry-groups">
          <section v-for="group in entryGroups" :key="group.title" class="entry-group card">
            <h2>{{ group.title }}</h2>
            <div class="entry-list"><router-link v-for="item in group.items" :key="item.path" :to="item.path"><el-icon :size="19"><component :is="item.icon" /></el-icon><span>{{ item.label }}</span><el-icon class="entry-arrow"><ArrowRight /></el-icon></router-link></div>
          </section>
        </div>
        <section v-if="!userStore.isAdmin" class="recent-panel card">
          <div class="recent-heading"><h2>最近积分记录</h2><router-link to="/profile/score" class="text-link">全部</router-link></div>
          <p class="section-note">查看获得、使用与退回积分的最近变化。</p>
          <p v-if="recentLoading" class="inline-status" role="status">正在读取记录…</p>
          <p v-else-if="recentError" class="inline-status" role="alert">记录暂时无法加载。<button type="button" @click="fetchRecentItems">重试</button></p>
          <ul v-else-if="recentItems.length" class="recent-list"><li v-for="(item, index) in recentItems" :key="item.record_id || index"><div><strong>{{ item.source_desc || '积分变动' }}</strong><time>{{ formatDate(item.create_time) }}</time></div><span :class="{ earned: item.change_amount > 0 }">{{ item.change_amount > 0 ? '+' : '' }}{{ item.change_amount }}</span></li></ul>
          <div v-else class="recent-empty"><el-icon :size="25"><Document /></el-icon><p>暂时没有积分记录</p><router-link to="/course" class="text-link">从浏览课程开始</router-link></div>
        </section>
      </div>
      <div class="logout-row"><el-button plain @click="handleLogout">退出登录</el-button></div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Reading, UploadFilled, Download, Star, Present, ShoppingCart, Service, ChatDotRound, Monitor, Setting, UserFilled, Document, ArrowRight, InfoFilled, Warning } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useUserStore } from '@/stores/user'
import { USER_TYPE_LABEL } from '@/utils/constants'
import { getScoreDetails, getScoreSummary } from '@/api/score'
import { getAuthSessionId, subscribeAuthSessionChange } from '@/utils/auth'
import * as authApi from '@/api/auth'

const router = useRouter(), userStore = useUserStore()
const roleLabel = computed(() => USER_TYPE_LABEL[userStore.userType] || '学生')
const quickEntries = computed(() => userStore.isAdmin ? [
  { label: '管理概览', path: '/admin/dashboard', icon: Monitor, description: '查看待办' },
  { label: '资料审核', path: '/admin/materials', icon: UploadFilled, description: '检查与处理' },
  { label: '举报管理', path: '/admin/reports', icon: Warning, description: '回看与处理' }
] : [
  { label: '我的选课', path: '/profile/courses', icon: Reading, description: '管理学习清单' },
  { label: '我的上传', path: '/material?tab=my', icon: UploadFilled, description: '查看审核进度' },
  { label: '我的消息', path: '/messages', icon: ChatDotRound, description: '与好友交流' }
])
const entryGroups = computed(() => [
  userStore.isAdmin ? { title: '管理与运营', items: [
    { label: '课程管理', path: '/admin/courses', icon: Reading }, { label: '评价管理', path: '/admin/reviews', icon: ChatDotRound },
    { label: '广场管理', path: '/admin/square', icon: ChatDotRound }, { label: '用户管理', path: '/admin/users', icon: UserFilled },
    { label: '商品管理', path: '/admin/goods', icon: ShoppingCart }, { label: '订单管理', path: '/admin/orders', icon: Present },
    { label: '首页公告', path: '/admin/banners', icon: Document }, { label: '反馈管理', path: '/profile/feedback', icon: Service }
  ] } : { title: '我的记录', items: [
    { label: '下载记录', path: '/profile/downloads', icon: Download }, { label: '我的收藏', path: '/profile/favorites', icon: Star },
    { label: '我的评价', path: '/profile/reviews', icon: ChatDotRound }, { label: '兑换订单', path: '/profile/orders', icon: Present }
  ] },
  { title: '交流与服务', items: [
    { label: '我的好友', path: '/contact?tab=friends', icon: UserFilled }, { label: '积分商城', path: '/mall', icon: ShoppingCart },
    { label: userStore.isAdmin ? '我的消息' : '反馈与举报', path: userStore.isAdmin ? '/messages' : '/profile/feedback', icon: ChatDotRound }, { label: '使用帮助', path: '/profile/support', icon: Service }
  ] },
  { title: '账号与设置', items: [
    { label: '账号安全', path: '/profile/security', icon: User }, { label: '设置', path: '/profile/settings', icon: Setting },
    { label: '关于选课通', path: '/profile/about', icon: InfoFilled }
  ] }
])
const monthStats = reactive({ earned: 0, spent: 0, refunded: 0 })
const avatarSaving = ref(false)
const statsLoading = ref(true), statsError = ref(false), recentLoading = ref(true), recentError = ref(false), recentItems = ref([])
let alive = true, statsRequest = 0, recentRequest = 0
async function fetchMonthStats() {
  const request = ++statsRequest, owner = getAuthSessionId()
  const owns = () => alive && owner === getAuthSessionId() && request === statsRequest
  statsLoading.value = true; statsError.value = false
  try {
    const now = new Date(), month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    const res = await getScoreSummary(month)
    if (owns()) Object.assign(monthStats, res.data)
  } catch { if (owns()) statsError.value = true }
  finally { if (owns()) statsLoading.value = false }
}
async function fetchRecentItems() {
  const request = ++recentRequest, owner = getAuthSessionId()
  const owns = () => alive && owner === getAuthSessionId() && request === recentRequest
  recentLoading.value = true; recentError.value = false
  try { const res = await getScoreDetails({ page: 1, pageSize: 4 }); if (owns()) recentItems.value = res.data?.list || res.data?.records || [] }
  catch { if (owns()) recentError.value = true }
  finally { if (owns()) recentLoading.value = false }
}
function formatDate(value) { if (!value) return ''; const date = new Date(value); return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }
async function onAvatarChange(event) {
  const file = event.target.files?.[0], owner = getAuthSessionId()
  event.target.value = ''
  if (!file || avatarSaving.value) return
  if (file.size > 2 * 1024 * 1024) { ElMessage.warning('头像不能超过 2MB'); return }
  avatarSaving.value = true
  try {
    const res = await authApi.uploadAvatar(file)
    if (alive && owner === getAuthSessionId()) { userStore.updateUserInfo({ avatar_url: `${res.data?.avatar_url}?v=${Date.now()}` }); ElMessage.success('头像已更新') }
  } catch { /* Request layer reports upload errors. */ }
  finally { if (alive && owner === getAuthSessionId()) avatarSaving.value = false }
}
async function handleLogout() {
  try { await ElMessageBox.confirm('确认退出登录吗？', '退出登录', { confirmButtonText: '退出', cancelButtonText: '取消' }); userStore.logout(); router.push('/login') } catch { /* Cancel leaves session active. */ }
}
function refreshData() { userStore.fetchUserInfo(); if (!userStore.isAdmin && getAuthSessionId()) { fetchMonthStats(); fetchRecentItems() } }
const unsubscribe = subscribeAuthSessionChange(() => { ++statsRequest; ++recentRequest; recentItems.value = []; Object.assign(monthStats, { earned: 0, spent: 0, refunded: 0 }); statsLoading.value = true; recentLoading.value = true })
onMounted(refreshData)
onBeforeUnmount(() => { alive = false; ++statsRequest; ++recentRequest; unsubscribe() })
</script>

<style scoped>
.profile-overview { display: grid; grid-template-columns: 1.05fr 1fr; gap: 20px; margin-bottom: 20px; }
.profile-identity, .profile-balance, .entry-group, .recent-panel { padding: 24px; border: 1px solid var(--color-border-light); border-radius: 14px; box-shadow: none; }
.profile-identity { display: flex; flex-wrap: wrap; align-items: center; gap: 18px; }
.profile-identity .el-avatar { flex-shrink: 0; }
.avatar-button { border: 0; padding: 0; background: transparent; border-radius: 50%; display: flex; cursor: pointer; flex-shrink: 0; }
.avatar-button:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 3px; }
.profile-identity__copy { min-width: 0; flex: 1; }
.profile-identity h2 { margin: 0; font-size: 22px; overflow-wrap: anywhere; }
.profile-identity p { margin: 7px 0 3px; color: var(--color-text-regular); font-size: 13px; }
.profile-identity__copy > span { color: var(--color-text-secondary); font-size: 12px; }
.profile-role-detail { width: 100%; color: var(--color-text-secondary); font-size: 13px; }
.text-link { color: var(--color-primary); font-size: 13px; text-decoration: none; padding: 8px 0; }
.profile-balance { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 16px; }
.profile-balance > div > span { color: var(--color-text-secondary); font-size: 13px; }
.profile-balance > div > strong { margin-left: 12px; color: var(--color-primary); font-size: 28px; }
.month-stats { grid-column: 1 / -1; margin: 0; display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; padding-top: 14px; border-top: 1px solid var(--color-border-light); }
.month-stats dt { font-size: 12px; color: var(--color-text-secondary); }
.month-stats dd { margin: 5px 0 0; font-weight: 700; font-size: 18px; }
.quick-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 24px; }
.quick-entry { display: grid; grid-template-columns: 34px 1fr; align-items: center; gap: 5px 10px; padding: 18px; color: var(--color-text-primary); border: 1px solid var(--color-border-light); background: var(--color-bg-card); border-radius: 12px; text-decoration: none; }
.quick-entry .el-icon { grid-row: 1 / 3; color: var(--color-primary); }
.quick-entry strong { font-size: 14px; }
.quick-entry > span { font-size: 12px; color: var(--color-text-secondary); }
.profile-body { display: grid; grid-template-columns: 1.4fr 1fr; gap: 22px; align-items: start; }
.entry-groups { display: grid; gap: 18px; }
.entry-group h2, .recent-heading h2 { font-size: 16px; margin: 0 0 8px; }
.entry-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 22px; }
.entry-list a { display: flex; align-items: center; gap: 10px; min-height: 50px; color: var(--color-text-regular); text-decoration: none; border-bottom: 1px solid var(--color-border-light); font-size: 13px; }
.entry-list a:hover, .quick-entry:hover { color: var(--color-primary); background: var(--color-bg-soft); }
.entry-list .entry-arrow { margin-left: auto; font-size: 12px; color: var(--color-text-placeholder); }
.recent-heading { display: flex; justify-content: space-between; align-items: center; }
.section-note { font-size: 12px; color: var(--color-text-secondary); margin: 0 0 12px; line-height: 1.6; }
.recent-list { list-style: none; padding: 0; margin: 0; }
.recent-list li { display: flex; justify-content: space-between; gap: 14px; padding: 14px 0; border-bottom: 1px solid var(--color-border-light); }
.recent-list strong { font-size: 13px; font-weight: 500; }
.recent-list time { display: block; margin-top: 5px; font-size: 12px; color: var(--color-text-secondary); }
.recent-list > li > span { font-size: 14px; font-weight: 650; }
.recent-list .earned { color: var(--color-success); }
.recent-empty { padding: 24px 0; text-align: center; color: var(--color-text-secondary); font-size: 13px; }
.inline-status { font-size: 13px; color: var(--color-text-secondary); grid-column: 1 / -1; }
.inline-status button { background: transparent; color: var(--color-primary); border: 0; padding: 8px; cursor: pointer; }
.logout-row { margin-top: 24px; text-align: right; }
@media (max-width: 1000px) { .profile-overview { grid-template-columns: 1fr; } .profile-body { grid-template-columns: 1fr; } }
@media (max-width: 767px) { .profile-identity, .profile-balance, .entry-group, .recent-panel { padding: 16px; } .profile-overview { gap: 14px; margin-bottom: 14px; } .profile-identity { gap: 12px; } .profile-identity h2 { font-size: 19px; } .quick-grid { gap: 8px; margin-bottom: 18px; } .quick-entry { grid-template-columns: 1fr; justify-items: center; gap: 9px; padding: 14px 6px; text-align: center; } .quick-entry .el-icon { grid-row: auto; } .quick-entry strong { font-size: 13px; } .quick-entry > span { display: none; } .entry-list { column-gap: 14px; } .entry-list a { min-height: 48px; font-size: 13px; } .profile-body { gap: 18px; } }
</style>
