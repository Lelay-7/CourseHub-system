<template>
  <AppLayout>
    <PageContainer>
      <PageHeader title="管理后台" subtitle="课程 · 资料 · 评价 · 广场 · 用户 · 商品 · 订单 · Banner · 反馈 · 举报" />
      <el-row :gutter="20">
        <el-col :span="24">
          <Card>
            <template #header>数据概览</template>
            <el-row :gutter="16" v-loading="statsLoading">
              <el-col :span="6">
                <div class="stat-box">
                  <div class="stat-box__value">{{ stats.courseCount ?? '-' }}</div>
                  <div class="stat-box__label">课程数</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat-box">
                  <div class="stat-box__value">{{ stats.userCount ?? '-' }}</div>
                  <div class="stat-box__label">用户数</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat-box">
                  <div class="stat-box__value">{{ stats.pendingMaterialCount ?? '-' }}</div>
                  <div class="stat-box__label">资料待审核数</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat-box">
                  <div class="stat-box__value">{{ stats.bannerCount ?? '-' }}</div>
                  <div class="stat-box__label">Banner数</div>
                </div>
              </el-col>
            </el-row>
          </Card>
        </el-col>
      </el-row>
      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="24">
          <Card>
            <template #header>管理模块</template>
            <div class="admin-shortcut-grid">
              <button v-for="item in shortcutEntries" :key="item.path" type="button" class="admin-shortcut-card" @click="$router.push(item.path)">
                <el-icon :size="20"><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </button>
            </div>
          </Card>
        </el-col>
      </el-row>
      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="24">
          <Card>
            <template #header>系统信息</template>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="系统版本">V3.0.0</el-descriptions-item>
              <el-descriptions-item label="当前角色">管理员</el-descriptions-item>
              <el-descriptions-item label="数据库">bgss_xtt (MySQL 8.0)</el-descriptions-item>
            </el-descriptions>
          </Card>
        </el-col>
      </el-row>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Reading, FolderOpened, ChatDotRound, User, Setting, ShoppingCart, EditPen, Present, Warning } from '@element-plus/icons-vue'
import AppLayout from '@/components/AppLayout.vue'
import PageContainer from '@/components/PageContainer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Card from '@/components/Card.vue'
import request from '@/api/request'

const route = useRoute()
const statsLoading = ref(false)
const shortcutEntries = [
  { path: '/admin/courses',   label: '课程管理', icon: Reading },
  { path: '/admin/materials', label: '资料审核', icon: FolderOpened },
  { path: '/admin/reviews',   label: '评价管理', icon: ChatDotRound },
  { path: '/admin/square',    label: '广场管理', icon: EditPen },
  { path: '/admin/users',     label: '用户管理', icon: User },
  { path: '/admin/goods',     label: '商品管理', icon: ShoppingCart },
  { path: '/admin/orders',    label: '订单管理', icon: Present },
  { path: '/admin/banners',   label: 'Banner',    icon: Setting },
  { path: '/admin/reports',   label: '举报管理', icon: Warning },
  { path: '/profile/feedback',label: '反馈管理', icon: EditPen }
]
const stats = reactive({
  bannerCount: null,
  userCount: null,
  courseCount: null,
  pendingMaterialCount: null
})

async function fetchDashboard() {
  statsLoading.value = true
  try {
    const res = await request.get('/api/admin/dashboard')
    if (res.data) {
      stats.bannerCount = res.data.bannerCount
      stats.userCount = res.data.userCount
      stats.courseCount = res.data.courseCount
      stats.pendingMaterialCount = res.data.pendingMaterialCount
    }
  } finally {
    statsLoading.value = false
  }
}

onMounted(fetchDashboard)
</script>

<style scoped>
.stat-box {
  text-align: center;
  padding: 16px 0;
}
.stat-box__value {
  font-size: 32px;
  font-weight: 900;
  color: var(--color-primary, #e87452);
  line-height: 1.3;
}
.stat-box__label {
  font-size: var(--font-size-xs, 12px);
  color: var(--color-text-secondary, #909399);
  margin-top: 4px;
}

.admin-shortcut-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
}
.admin-shortcut-card {
  appearance: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 18px 12px;
  border: 1px solid var(--color-border, #ecd8c9);
  border-radius: var(--radius-md, 18px);
  background: var(--color-bg-soft, #fff0e4);
  color: var(--color-text-primary, #3f342d);
  font-size: var(--font-size-sm, 13px);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--transition-fast), box-shadow var(--transition-fast), transform var(--transition-fast);
}
.admin-shortcut-card:hover {
  background: var(--color-primary-light, #ffe2d1);
  box-shadow: var(--shadow-sm);
  transform: translateY(-2px);
}
</style>
