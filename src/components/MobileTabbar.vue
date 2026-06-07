<template>
  <div class="mobile-tabbar">
    <div
      v-for="item in tabs"
      :key="item.path"
      class="tabbar-item"
      :class="{ active: isActive(item.path) }"
      @click="navigate(item.path)"
    >
      <el-icon :size="20">
        <component :is="item.icon" />
      </el-icon>
      <span class="tabbar-label">{{ item.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled, Reading, FolderOpened, ShoppingCart, UserFilled, User } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const tabs = [
  { path: '/home', label: '首页', icon: HomeFilled },
  { path: '/course', label: '课程', icon: Reading },
  { path: '/material', label: '资料', icon: FolderOpened },
  { path: '/mall', label: '商城', icon: ShoppingCart },
  { path: '/contact', label: '通讯录', icon: UserFilled },
  { path: '/profile', label: '我的', icon: User }
]

function isActive(path) {
  if (path === '/home') return route.path === '/home'
  return route.path.startsWith(path)
}

function navigate(path) {
  router.push(path)
}
</script>

<style scoped>
.mobile-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  min-height: var(--tabbar-height);
  display: flex;
  align-items: center;
  background: rgba(255, 253, 248, 0.92);
  border-top: 1px solid rgba(22, 105, 122, 0.1);
  box-shadow: 0 -12px 28px rgba(28, 51, 60, 0.08);
  backdrop-filter: blur(14px);
  z-index: 100;
  padding: 5px 4px env(safe-area-inset-bottom, 0);
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  min-width: 0;
  height: 50px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: color var(--transition-fast), background var(--transition-fast), transform var(--transition-fast);
}
.tabbar-item.active {
  color: var(--color-primary-dark);
  background: var(--color-bg-soft);
  transform: translateY(-2px);
}

.tabbar-label {
  width: 100%;
  font-size: 11px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
