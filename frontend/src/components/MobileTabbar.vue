<template>
  <nav class="mobile-tabbar">
    <button
      v-for="item in tabs"
      :key="item.path"
      type="button"
      class="tabbar-item"
      :class="{ active: isActive(item.path) }"
      @click="navigate(item.path)"
    >
      <el-icon :size="20"><component :is="item.icon" /></el-icon>
      <span class="tabbar-label">{{ item.label }}</span>
    </button>
  </nav>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled, Reading, ChatDotRound, FolderOpened, User } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const tabs = [
  { path: '/home', label: '首页', icon: HomeFilled },
  { path: '/course', label: '选课', icon: Reading },
  { path: '/square', label: '广场', icon: ChatDotRound },
  { path: '/material', label: '资料', icon: FolderOpened },
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
  right: 0;
  bottom: 0;
  left: 0;
  min-height: var(--tabbar-height);
  display: flex;
  align-items: center;
  padding: 5px 4px env(safe-area-inset-bottom, 0);
  background: rgba(255, 250, 243, 0.94);
  border-top: 1px solid rgba(154, 66, 45, 0.1);
  box-shadow: 0 -12px 28px rgba(106, 70, 43, 0.1);
  backdrop-filter: blur(14px);
  z-index: 100;
}

.tabbar-item {
  flex: 1;
  height: 50px;
  min-width: 0;
  border: 0;
  border-radius: 16px;
  color: var(--color-text-secondary);
  background: transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  cursor: pointer;
  transition: color var(--transition-fast), background var(--transition-fast), transform var(--transition-fast);
}

.tabbar-item.active {
  color: #fffaf3;
  background: var(--color-primary);
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(154, 66, 45, 0.18);
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
