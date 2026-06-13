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
        <el-col :span="8">
          <Card>
            <template #header>快捷入口</template>
            <el-menu :default-active="route.path" router>
              <el-menu-item index="/admin/courses">
                <el-icon><Reading /></el-icon> 课程管理
              </el-menu-item>
              <el-menu-item index="/admin/materials">
                <el-icon><FolderOpened /></el-icon> 资料审核
              </el-menu-item>
              <el-menu-item index="/admin/reviews">
                <el-icon><ChatDotRound /></el-icon> 评价管理
              </el-menu-item>
              <el-menu-item index="/admin/square">
                <el-icon><EditPen /></el-icon> 广场管理
              </el-menu-item>
              <el-menu-item index="/admin/users">
                <el-icon><User /></el-icon> 用户管理
              </el-menu-item>
              <el-menu-item index="/admin/goods">
                <el-icon><ShoppingCart /></el-icon> 商品管理
              </el-menu-item>
              <el-menu-item index="/admin/orders">
                <el-icon><Present /></el-icon> 订单管理
              </el-menu-item>
              <el-menu-item index="/admin/banners">
                <el-icon><Setting /></el-icon> Banner管理
              </el-menu-item>
              <el-menu-item index="/admin/reports">
                <el-icon><Warning /></el-icon> 举报管理
              </el-menu-item>
              <el-menu-item index="/profile/feedback">
                <el-icon><EditPen /></el-icon> 反馈管理
              </el-menu-item>
            </el-menu>
          </Card>
        </el-col>
        <el-col :span="16">
          <Card>
            <template #header>系统信息</template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="系统版本">V3.0.0</el-descriptions-item>
              <el-descriptions-item label="当前角色">管理员</el-descriptions-item>
              <el-descriptions-item label="管理模块">课程 · 资料 · 评价 · 广场 · 用户 · 商品 · 订单 · Banner · 反馈 · 举报</el-descriptions-item>
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
</style>
