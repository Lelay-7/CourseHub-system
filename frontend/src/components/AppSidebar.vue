<template>
  <aside class="app-sidebar">
    <div class="sidebar-logo">
      <el-icon :size="24"><School /></el-icon>
    </div>

    <nav class="sidebar-menu">
      <template v-for="item in menus" :key="item.path || 'divider'">
        <div v-if="item.type === 'divider'" class="sidebar-divider"></div>
        <el-tooltip
          v-else
          :content="item.label"
          placement="right"
        >
          <button
            type="button"
            class="sidebar-item"
            :class="{ active: activeMenu === item.path }"
            @click="$router.push(item.path)"
          >
            <el-icon :size="22"><component :is="item.icon" /></el-icon>
          </button>
        </el-tooltip>
      </template>
    </nav>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  School,
  HomeFilled,
  Reading,
  ChatDotRound,
  FolderOpened,
  UserFilled,
  ShoppingCart,
  User,
  Setting,
  Monitor,
  EditPen,
  Present,
  Warning
} from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()

// 学生/教师菜单
const studentMenus = [
  { path: '/home', label: '首页', icon: HomeFilled },
  { path: '/course', label: '选课中心', icon: Reading },
  { path: '/square', label: '同学广场', icon: ChatDotRound },
  { path: '/material', label: '学习资料', icon: FolderOpened },
  { path: '/contact', label: '师生通讯录', icon: UserFilled },
  { path: '/mall', label: '积分商城', icon: ShoppingCart },
  { path: '/profile', label: '个人中心', icon: User }
]

// 管理员菜单
const adminMenus = [
  { path: '/admin/dashboard', label: '管理后台', icon: Monitor },
  { path: '/admin/courses', label: '课程管理', icon: Reading },
  { path: '/admin/materials', label: '资料审核', icon: FolderOpened },
  { path: '/admin/reviews', label: '评价管理', icon: ChatDotRound },
  { path: '/admin/square', label: '广场管理', icon: EditPen },
  { path: '/admin/users', label: '用户管理', icon: User },
  { path: '/admin/goods', label: '商品管理', icon: ShoppingCart },
  { path: '/admin/orders', label: '订单管理', icon: Present },
  { path: '/admin/banners', label: 'Banner', icon: Setting },
  { path: '/admin/reports', label: '举报管理', icon: Warning },
  { path: '/profile/feedback', label: '反馈管理', icon: EditPen },
  { type: 'divider' },
  { path: '/home', label: '前台首页', icon: HomeFilled },
  { path: '/profile', label: '个人中心', icon: UserFilled }
]

const menus = computed(() => userStore.isAdmin ? adminMenus : studentMenus)

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/admin/courses')) return '/admin/courses'
  if (path.startsWith('/admin/materials')) return '/admin/materials'
  if (path.startsWith('/admin/reviews')) return '/admin/reviews'
  if (path.startsWith('/admin/square')) return '/admin/square'
  if (path.startsWith('/admin/users')) return '/admin/users'
  if (path.startsWith('/admin/banners')) return '/admin/banners'
  if (path.startsWith('/admin/orders')) return '/admin/orders'
  if (path.startsWith('/admin/goods')) return '/admin/goods'
  if (path.startsWith('/admin/reports')) return '/admin/reports'
  if (path.startsWith('/admin')) return '/admin/dashboard'
  if (path.startsWith('/course')) return '/course'
  if (path.startsWith('/square')) return '/square'
  if (path.startsWith('/material')) return '/material'
  if (path.startsWith('/contact')) return '/contact'
  if (path.startsWith('/mall')) return '/mall'
  if (path.startsWith('/profile')) return '/profile'
  return '/home'
})
</script>

<style scoped>
.app-sidebar {
  width: 72px;
  height: 100vh;
  flex: 0 0 72px;
  display: flex;
  flex-direction: column;
  align-items: center;
  background:
    linear-gradient(180deg, rgba(255, 246, 234, 0.32), transparent 38%),
    linear-gradient(180deg, #e08873 0%, #d0735a 100%);
  box-shadow: 10px 0 24px rgba(125, 62, 38, 0.1);
}

.sidebar-logo {
  width: 72px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fffaf3;
}

.sidebar-menu {
  width: 100%;
  padding: 10px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.sidebar-item {
  appearance: none;
  width: 48px;
  height: 48px;
  border: 0;
  border-radius: 18px;
  color: rgba(255, 250, 243, 0.86);
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: color var(--transition-fast), background var(--transition-fast), box-shadow var(--transition-fast);
}

.sidebar-item:hover {
  color: #fffaf3;
  background: rgba(255, 250, 243, 0.16);
}

.sidebar-item.active {
  color: var(--color-primary-dark);
  background: #fffaf3;
  box-shadow: 0 10px 22px rgba(125, 62, 38, 0.18);
}

.sidebar-divider {
  width: 32px;
  margin: 6px 0;
  border-top: 1px solid rgba(255, 250, 243, 0.28);
}
</style>
