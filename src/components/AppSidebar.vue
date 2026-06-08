<template>
  <aside class="app-sidebar">
    <div class="sidebar-logo">
      <el-icon :size="24"><School /></el-icon>
    </div>

    <nav class="sidebar-menu">
      <el-tooltip
        v-for="item in menus"
        :key="item.path"
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
    </nav>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  School,
  HomeFilled,
  Reading,
  ChatDotRound,
  FolderOpened,
  UserFilled,
  ShoppingCart,
  User
} from '@element-plus/icons-vue'

const route = useRoute()

const menus = [
  { path: '/home', label: '首页', icon: HomeFilled },
  { path: '/course', label: '选课中心', icon: Reading },
  { path: '/square', label: '同学广场', icon: ChatDotRound },
  { path: '/material', label: '学习资料', icon: FolderOpened },
  { path: '/contact', label: '师生通讯录', icon: UserFilled },
  { path: '/mall', label: '积分商城', icon: ShoppingCart },
  { path: '/profile', label: '个人中心', icon: User }
]

const activeMenu = computed(() => {
  const path = route.path
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
    linear-gradient(180deg, rgba(255, 246, 234, 0.24), transparent 34%),
    linear-gradient(180deg, #fa7f70 0%, #e7664a 100%);
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
</style>
