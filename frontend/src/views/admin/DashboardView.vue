<template>
  <AppLayout><PageContainer>
    <PageHeader title="管理后台" subtitle="先处理待办，再维护课程与校园内容。" />
    <ErrorState v-if="error" :title="error" @retry="fetchDashboard" />
    <section class="dashboard-tasks" aria-label="管理待办" v-loading="loading">
      <router-link v-for="task in tasks" :key="task.path" :to="task.path" class="task-card">
        <span class="task-label">{{ task.title }}</span><strong>{{ task.path === '/admin/materials' ? (stats.pendingMaterialCount ?? '—') : task.action }}</strong><span>{{ task.description }}</span><span class="task-open">前往处理 →</span>
      </router-link>
    </section>
    <section class="dashboard-stats"><div><strong>{{ stats.courseCount ?? '—' }}</strong><span>课程</span></div><div><strong>{{ stats.userCount ?? '—' }}</strong><span>用户</span></div><div><strong>{{ stats.bannerCount ?? '—' }}</strong><span>首页轮播</span></div></section>
    <h2>内容与业务管理</h2>
    <nav class="dashboard-nav" aria-label="全部管理模块">
      <router-link v-for="entry in entries" :key="entry.path" :to="entry.path"><strong>{{ entry.title }}</strong><span>{{ entry.description }}</span></router-link>
    </nav>
  </PageContainer></AppLayout>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { getDashboard } from '@/api/admin'
import { useRequestGate } from '@/composables/useListQuery'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import ErrorState from '@/components/ErrorState.vue'
const stats = ref({}), loading = ref(false), error = ref('')
const gate = useRequestGate(() => { stats.value = {}; loading.value = false })
const tasks = [
  { title: '资料待审核', path: '/admin/materials', action: '检查文件', description: '核查文件、版本与审核历史' },
  { title: '兑换订单', path: '/admin/orders', action: '安排交付', description: '填写领取、发放或服务说明' },
  { title: '资料举报', path: '/admin/reports', action: '核查处理', description: '给出结论，向举报人反馈' },
  { title: '用户反馈', path: '/profile/feedback', action: '查看回复', description: '处理问题和改进建议' }
]
const entries = [
  { title: '课程管理', path: '/admin/courses', description: '课程信息、教师和上架状态' },
  { title: '资料审核', path: '/admin/materials', description: '资料提交与修改记录' },
  { title: '评价管理', path: '/admin/reviews', description: '课程评价内容维护' },
  { title: '广场管理', path: '/admin/square', description: '动态与评论管理' },
  { title: '用户管理', path: '/admin/users', description: '用户信息与账号状态' },
  { title: '商品管理', path: '/admin/goods', description: '积分、库存与兑换限制' },
  { title: '订单管理', path: '/admin/orders', description: '订单内容和交付结果' },
  { title: '轮播管理', path: '/admin/banners', description: '首页图片与关联链接' },
  { title: '举报管理', path: '/admin/reports', description: '核查结论与下架处理' },
  { title: '反馈管理', path: '/profile/feedback', description: '用户反馈与回复' }
]
async function fetchDashboard() {
  const ticket = gate.begin(); loading.value = true; error.value = ''
  try { const res = await getDashboard(); if (gate.valid(ticket)) stats.value = res.data || {} }
  catch (err) { if (gate.valid(ticket) && !err.sessionChanged) error.value = '概览加载失败' }
  finally { if (gate.valid(ticket)) loading.value = false }
}
onMounted(fetchDashboard)
</script>
<style scoped>
.dashboard-tasks { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap:16px; }
.task-card { display:flex; flex-direction:column; gap:12px; background:var(--color-bg-card); padding:24px; border:1px solid var(--color-border); border-radius:18px; text-decoration:none; color:var(--color-text-secondary); }
.task-card strong { font-size:24px; color:var(--color-text-primary); }.task-label { font-weight:700; }.task-open { color:var(--color-primary); font-weight:700; }
.dashboard-stats { display:flex; gap:32px; flex-wrap:wrap; padding:24px 0; }.dashboard-stats div { display:flex; align-items:baseline; gap:8px; }.dashboard-stats strong { font-size:24px; }.dashboard-stats span { color:var(--color-text-secondary); }
.dashboard-nav { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; }.dashboard-nav a { display:flex; flex-direction:column; gap:8px; padding:20px; border:1px solid var(--color-border); border-radius:12px; background:var(--color-bg-card); color:var(--color-text-primary); text-decoration:none; }.dashboard-nav span { color:var(--color-text-secondary); font-size:13px; }
@media(max-width:1024px) { .dashboard-tasks { grid-template-columns:repeat(2,minmax(0,1fr)); } }
@media(max-width:600px) { .task-card { padding:16px; }.dashboard-nav { grid-template-columns:1fr; } .dashboard-nav a { padding:16px; } }
</style>
